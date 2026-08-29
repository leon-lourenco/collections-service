# collections-service

Part of [`card-billing-modernization`](https://github.com/leon-lourenco/card-billing-modernization) —
the modernized counterpart to [`card-billing-legacy`](https://github.com/leon-lourenco/card-billing-legacy).
Full architecture, contracts, and cross-cutting decisions live in that repo's
[`ARCHITECTURE.md`](https://github.com/leon-lourenco/card-billing-modernization/blob/master/ARCHITECTURE.md) —
this README covers what's specific to this one service.

Merges the legacy's `delinquency` and `interest-accrual` modules — they read the same overdue
invoices and act at the same point in the billing cycle, so splitting them into two services
would mean fetching the same data twice for no real gain in isolation.

**Owns no database of its own.** Every read and every mutation goes through `billing-service`'s
API — this service decides *what* should happen to an overdue invoice; `billing-service` is the
only one that actually writes to invoice state.

## Flow

`POST /collections/run?date=`, plus its own daily `@Scheduled` run:

1. `GET /invoices/overdue` from `billing-service` — wrapped in Resilience4j (circuit breaker +
   retry) and cached in Redis (short TTL). If `billing-service` is unreachable, this service
   keeps answering from the cache instead of failing outright — see
   [Resilience](#resilience-the-evidence) below.
2. For each overdue invoice: compute interest (2% flat fee the first time it goes overdue, 1%
   simple daily interest after that — same rule as the legacy) and call
   `POST /invoices/{id}/interest` on `billing-service`.
3. Compute the escalation stage from days overdue (D+5 / D+15 / D+30) and call
   `POST /notifications` on `notification-service`.

## Resilience — the evidence

This is the service the platform's resilience demo is captured against. The run below is real:
`billing-service` was killed mid-integration-test and this service kept answering
`/collections/run` instead of cascading the failure, then recovered cleanly once
`billing-service` came back — captured from live Resilience4j/Actuator metrics, not simulated.

**Baseline**, `billing-service` up: `{"invoiceSource":"LIVE","degraded":false,"overdueInvoices":146,"interestApplied":146,"failures":0}`.

**`billing-service` killed, run repeated with the cache past its freshness window**: every
attempt still answers `200`, degrading to the last known-good snapshot instead of failing —
`{"invoiceSource":"CACHED_STALE","degraded":true,"overdueInvoices":292,"failures":292}`. The
`failures` count is genuine: each of those 292 is a real attempt to POST interest against a dead
`billing-service`, caught per-invoice so one bad invoice never aborts the rest of the run (see
`RunCollectionsUseCase`'s per-invoice `try/catch` — a deliberate break from the legacy, where a
single failure inside a shared `@Transactional` rolled back every invoice already processed).

**The breaker itself**, from `/actuator/circuitbreakerevents` on `billing-overdue-invoices`,
unedited:

```
13:04:28.096  FAILURE_RATE_EXCEEDED
13:04:28.109  State transition from CLOSED to OPEN
13:04:28.318  NOT_PERMITTED                          # next call fails fast, no network attempt
13:04:38.268  State transition from OPEN to HALF_OPEN  # 10.159s later — wait-duration-in-open-state is 10s
```

**Recovery**, `billing-service` restarted: `billing-apply-interest` and `notification-request`
(which independently tripped open too, under the same load) both closed on the next successful
call; a final run against fresh data came back
`{"invoiceSource":"LIVE","degraded":false,"overdueInvoices":292,"notificationsRequested":584,"failures":0}` —
full recovery, no manual intervention.

Errors are `application/problem+json` via this service's own domain exceptions —
`BillingServiceUnavailableException` (raised only once even the cache is empty),
`InvalidEscalationStageException`.

## Engineering practices

Hexagonal package structure (`domain` / `application` / `infrastructure`), enforced by ArchUnit
in every test run — see `ARCHITECTURE.md` in the hub repo for the exact rules. Tests written
alongside implementation, not after.

## Stack

| Category | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 4.1.0 |
| Build | Gradle (Kotlin DSL) | 9.7.1 |
| API docs | springdoc-openapi-starter-webmvc-ui | 3.1.0 |
| Resilience | resilience4j-spring-boot4 | 2.4.0 |
| Cache | Redis | 8 |
| Auth | Keycloak (client + resource server) | 26.7 |
| Architecture tests | ArchUnit | — |

## Running it

```bash
docker compose up -d          # Redis (6379) + Keycloak (8182)
./gradlew bootRun
```

No Postgres — this service owns no database. Keycloak sits on 8182 rather than a default port so it
does not collide with the other three services' own solo stacks; the realm imported there holds
just this service's client. A `card-billing-shared` repo — one version catalog and one realm
export, consumed as a git submodule by all four services instead of each keeping its own copy —
is the next planned step; see the hub repo's `INTEGRATION.md`.

Needs `billing-service` and `notification-service` reachable for a real end-to-end run — see the
hub repo's full-platform `docker-compose.yml` to bring up all four together. Swagger UI at
`http://localhost:8082/swagger-ui.html`.

```bash
./gradlew test
```

Outbound calls to `billing-service` and `notification-service` are stubbed with WireMock in this
service's own tests, matching the contract documented in the hub repo's `ARCHITECTURE.md` — this
service's test suite doesn't require the other three running.
