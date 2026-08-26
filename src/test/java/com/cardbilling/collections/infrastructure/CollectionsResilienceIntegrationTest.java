package com.cardbilling.collections.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cardbilling.collections.domain.Freshness;
import com.cardbilling.collections.support.DownstreamServicesTestBase;
import com.cardbilling.collections.support.FixedClockTestConfig;
import com.cardbilling.collections.support.MutableClock;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The resilience evidence, run for real: a live {@code billing-service} call, a Redis cache entry,
 * and then {@code billing-service} taken away three different ways — a 5xx, a read timeout, and an
 * open circuit breaker — with this service still answering from cache each time.
 *
 * <p>Redis is a real container and {@code billing-service}/{@code notification-service} are
 * WireMock servers stubbed to the contracts in {@code ARCHITECTURE.md}. The clock is driven by hand
 * so crossing a sixty-second freshness window does not cost the suite sixty seconds.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(FixedClockTestConfig.class)
@TestPropertySource(
        properties = {
            // No Keycloak in this suite: outbound tokens are switched off, and inbound tokens are
            // supplied directly by MockMvc's jwt() post-processor.
            "collections.outbound-auth.enabled=false",
            "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:1/certs",
            "collections.schedule.enabled=false",
            // The production 2s is fine against a Redis on the same network; a container reached
            // through Docker Desktop's port proxy on a cold first connection is not that.
            "spring.data.redis.timeout=15s",
            "spring.data.redis.connect-timeout=15s",
            "collections.cache.fresh-for=60s",
            "collections.cache.retain-for=10m",
            "collections.notification.channels=EMAIL",
            // Short enough that a deliberately delayed stub trips it, generous enough that the
            // first call out of a cold context - JVM, RestClient, and WireMock's Jetty all warming
            // up at once - is not mistaken for a downstream that is down.
            "collections.billing-service.read-timeout=2s",
            // Deterministic breaker: three retried attempts per run means the six-call window
            // fills after exactly two failing runs.
            "resilience4j.circuitbreaker.instances.billing-overdue-invoices.sliding-window-size=6",
            "resilience4j.circuitbreaker.instances.billing-overdue-invoices.minimum-number-of-calls=6",
            "resilience4j.circuitbreaker.instances.billing-overdue-invoices.wait-duration-in-open-state=60s",
            "resilience4j.circuitbreaker.instances.billing-overdue-invoices."
                    + "automatic-transition-from-open-to-half-open-enabled=false",
            "resilience4j.retry.configs.default.wait-duration=10ms",
            "resilience4j.retry.configs.default.enable-exponential-backoff=false"
        })
class CollectionsResilienceIntegrationTest extends DownstreamServicesTestBase {

    private static final LocalDate AS_OF = LocalDate.of(2026, 8, 25);
    private static final String CACHE_KEY = "billing:invoices:overdue:2026-08-25";

    /** Two invoices: one never accrued and 10 days late, one accrued yesterday and 30 days late. */
    private static final String OVERDUE_PAYLOAD =
            """
            [
              {
                "invoiceId": "inv-1",
                "customerId": "cus-1",
                "totalAmountCents": 125000,
                "currency": "BRL",
                "dueDate": "2026-08-15",
                "lastInterestAccrualDate": null
              },
              {
                "invoiceId": "inv-2",
                "customerId": "cus-2",
                "totalAmountCents": 80000,
                "currency": "BRL",
                "dueDate": "2026-07-26",
                "lastInterestAccrualDate": "2026-08-24"
              }
            ]
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private Clock clock;

    private MutableClock testClock() {
        return (MutableClock) clock;
    }

    @BeforeEach
    void resetEverything() {
        BILLING_SERVICE.resetAll();
        NOTIFICATION_SERVICE.resetAll();

        Set<String> keys = redis.keys("billing:invoices:overdue:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        circuitBreakerRegistry.circuitBreaker("billing-overdue-invoices").reset();
        circuitBreakerRegistry.circuitBreaker("billing-apply-interest").reset();
        circuitBreakerRegistry.circuitBreaker("notification-request").reset();
        testClock().setTo(FixedClockTestConfig.START);

        NOTIFICATION_SERVICE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/notifications"))
                .willReturn(WireMock.aResponse().withStatus(202)));
        BILLING_SERVICE.stubFor(WireMock.post(WireMock.anyUrl())
                .atPriority(10)
                .willReturn(WireMock.aResponse().withStatus(204)));
    }

    @Test
    @DisplayName("a healthy run reads billing-service live, acts on it, and caches the result")
    void runs_live_and_populates_the_cache() throws Exception {
        stubOverdueInvoices();

        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceSource").value(Freshness.LIVE.name()))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.overdueInvoices").value(2))
                .andExpect(jsonPath("$.interestApplied").value(2))
                .andExpect(jsonPath("$.notificationsRequested").value(2))
                .andExpect(jsonPath("$.failures").value(0));

        assertThat(redis.hasKey(CACHE_KEY)).isTrue();
        BILLING_SERVICE.verify(1, overdueRequest());

        // The flat 2% fee applies only to the invoice that has never accrued before; both get 1%.
        BILLING_SERVICE.verify(
                1,
                WireMock.postRequestedFor(WireMock.urlPathEqualTo("/invoices/inv-1/interest"))
                        .withRequestBody(WireMock.equalToJson(
                                """
                                {"feeCents":2500,"dailyInterestCents":1250,"accrualDate":"2026-08-25"}""")));
        BILLING_SERVICE.verify(
                1,
                WireMock.postRequestedFor(WireMock.urlPathEqualTo("/invoices/inv-2/interest"))
                        .withRequestBody(WireMock.equalToJson(
                                """
                                {"feeCents":0,"dailyInterestCents":800,"accrualDate":"2026-08-25"}""")));

        // 10 days late is the D+5 reminder; 30 days late is the formal notice.
        NOTIFICATION_SERVICE.verify(
                1,
                WireMock.postRequestedFor(WireMock.urlPathEqualTo("/notifications"))
                        .withRequestBody(WireMock.equalToJson(
                                """
                                {"customerId":"cus-1","invoiceId":"inv-1","channel":"EMAIL",\
                                "stage":"REMINDER_D5"}""")));
        NOTIFICATION_SERVICE.verify(
                1,
                WireMock.postRequestedFor(WireMock.urlPathEqualTo("/notifications"))
                        .withRequestBody(WireMock.equalToJson(
                                """
                                {"customerId":"cus-2","invoiceId":"inv-2","channel":"EMAIL",\
                                "stage":"FORMAL_NOTICE_D30"}""")));
    }

    @Test
    @DisplayName("inside the freshness window billing-service is not called at all")
    void serves_a_fresh_cache_entry_without_calling_billing_service() throws Exception {
        stubOverdueInvoices();
        runExpectingOk();

        testClock().advance(Duration.ofSeconds(30));

        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceSource").value(Freshness.CACHED_FRESH.name()))
                .andExpect(jsonPath("$.degraded").value(false))
                .andExpect(jsonPath("$.overdueInvoices").value(2));

        BILLING_SERVICE.verify(1, overdueRequest());
    }

    @Test
    @DisplayName("billing-service returning 5xx: the stale cache is served instead of failing")
    void serves_the_stale_cache_when_billing_service_errors() throws Exception {
        stubOverdueInvoices();
        runExpectingOk();

        // Past the freshness window, so the cache alone is no longer enough...
        testClock().advance(Duration.ofSeconds(90));
        stubOverdueInvoicesFailing();

        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceSource").value(Freshness.CACHED_STALE.name()))
                .andExpect(jsonPath("$.degraded").value(true))
                // ...and the run still acted on the same two invoices it saw while billing was up.
                .andExpect(jsonPath("$.overdueInvoices").value(2))
                .andExpect(jsonPath("$.interestApplied").value(2))
                .andExpect(jsonPath("$.notificationsRequested").value(2));

        // One live call, then three retried attempts before the fallback took over.
        BILLING_SERVICE.verify(4, overdueRequest());
    }

    @Test
    @DisplayName("billing-service hanging past the read timeout is handled the same way")
    void serves_the_stale_cache_when_billing_service_times_out() throws Exception {
        stubOverdueInvoices();
        runExpectingOk();

        testClock().advance(Duration.ofSeconds(90));
        BILLING_SERVICE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/invoices/overdue"))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(OVERDUE_PAYLOAD)
                        .withFixedDelay(5_000)));

        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceSource").value(Freshness.CACHED_STALE.name()))
                .andExpect(jsonPath("$.overdueInvoices").value(2));
    }

    @Test
    @DisplayName("once the circuit opens the fallback still serves, and billing-service is left alone")
    void serves_the_stale_cache_while_the_circuit_is_open() throws Exception {
        stubOverdueInvoices();
        runExpectingOk();

        testClock().advance(Duration.ofSeconds(90));
        stubOverdueInvoicesFailing();

        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker("billing-overdue-invoices");

        // Two runs at three retried attempts each fill the six-call window with failures.
        runExpectingOk();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        runExpectingOk();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int callsWhileClosed = overdueRequestCount();

        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceSource").value(Freshness.CACHED_STALE.name()))
                .andExpect(jsonPath("$.degraded").value(true))
                .andExpect(jsonPath("$.overdueInvoices").value(2));

        // The point of an open breaker: the run succeeded without touching billing-service at all.
        assertThat(overdueRequestCount()).isEqualTo(callsWhileClosed);
    }

    @Test
    @DisplayName("no live call and nothing cached is the one case that fails, as problem+json")
    void fails_with_a_problem_detail_when_the_cache_is_empty_too() throws Exception {
        // No prior successful run, so Redis holds nothing for this date.
        stubOverdueInvoicesFailing();

        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()).with(jwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://cardbilling.example/problems/billing-service-unavailable"))
                .andExpect(jsonPath("$.title").value("billing-service unavailable"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.asOf").value("2026-08-25"))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("a cache entry Redis has already expired is not a fallback any more")
    void fails_once_the_cached_entry_has_been_evicted() throws Exception {
        stubOverdueInvoices();
        runExpectingOk();

        // Stands in for Redis expiring the key at collections.cache.retain-for.
        redis.delete(CACHE_KEY);
        testClock().advance(Duration.ofSeconds(90));
        stubOverdueInvoicesFailing();

        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()).with(jwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("billing-service unavailable"));
    }

    @Test
    @DisplayName("a failing notification-service costs that invoice, not the whole run")
    void records_a_per_invoice_failure_without_abandoning_the_run() throws Exception {
        stubOverdueInvoices();
        NOTIFICATION_SERVICE.resetAll();
        NOTIFICATION_SERVICE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/notifications"))
                .withRequestBody(WireMock.matchingJsonPath("$.invoiceId", WireMock.equalTo("inv-1")))
                .willReturn(WireMock.aResponse().withStatus(500)));
        NOTIFICATION_SERVICE.stubFor(WireMock.post(WireMock.urlPathEqualTo("/notifications"))
                .atPriority(10)
                .willReturn(WireMock.aResponse().withStatus(202)));

        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overdueInvoices").value(2))
                .andExpect(jsonPath("$.failures").value(1))
                // inv-2, queued after the failing invoice, was still processed.
                .andExpect(jsonPath("$.notificationsRequested").value(1))
                .andExpect(jsonPath("$.interestApplied").value(2));
    }

    @Test
    @DisplayName("the trigger endpoint is not reachable without a token")
    void rejects_an_unauthenticated_run() throws Exception {
        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the health probe stays open so an orchestrator can reach it")
    void allows_an_unauthenticated_health_probe() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private void stubOverdueInvoices() {
        BILLING_SERVICE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/invoices/overdue"))
                .withQueryParam("asOf", WireMock.equalTo(AS_OF.toString()))
                .willReturn(WireMock.aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(OVERDUE_PAYLOAD)));
    }

    private void stubOverdueInvoicesFailing() {
        BILLING_SERVICE.stubFor(WireMock.get(WireMock.urlPathEqualTo("/invoices/overdue"))
                .willReturn(WireMock.aResponse().withStatus(500).withBody("boom")));
    }

    private void runExpectingOk() throws Exception {
        mockMvc.perform(post("/collections/run").param("date", AS_OF.toString()).with(jwt()))
                .andExpect(status().isOk());
    }

    private int overdueRequestCount() {
        return BILLING_SERVICE.countRequestsMatching(overdueRequest().build()).getCount();
    }

    private static RequestPatternBuilder overdueRequest() {
        return WireMock.getRequestedFor(WireMock.urlPathEqualTo("/invoices/overdue"));
    }
}
