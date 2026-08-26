package com.cardbilling.collections.infrastructure.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Health indicator for the downstream {@code billing-service} dependency.
 *
 * <p>Reuses the configured {@code billingServiceRestClient} rather than building its own from a
 * bare builder: that client already carries the base URL from {@code collections.billing-service.
 * base-url} and, more importantly, connect and read timeouts. A health check with no read timeout
 * is a health check that can hang the health endpoint it is reporting to.
 */
@Component
public class BillingServiceHealthIndicator extends AbstractHealthIndicator {

    private final RestClient restClient;

    public BillingServiceHealthIndicator(@Qualifier("billingServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        try {
            String response = restClient.get()
                .uri("/actuator/health")
                .retrieve()
                .body(String.class);

            if (response != null && response.contains("UP")) {
                builder.up().withDetail("service", "billing-service");
            } else {
                degraded(builder).withDetail("service", "billing-service");
            }
        } catch (Exception ex) {
            degraded(builder).withDetail("service", "billing-service").withException(ex);
        }
    }

    /**
     * DEGRADED, never DOWN.
     *
     * <p>This service is built specifically to keep working when {@code billing-service} is not:
     * it serves the last cached overdue-invoice set and reports the run as degraded. Reporting
     * DOWN here would make {@code /actuator/health} return 503 during exactly the outage this
     * service is designed to survive, and an orchestrator reading that probe would restart a pod
     * that is doing its job correctly — turning one service's outage into two.
     *
     * <p>{@code DEGRADED} is mapped to HTTP 200 in {@code application.yml} while still ranking
     * above {@code UP} in the aggregate, so the information is visible without being fatal.
     */
    private static Health.Builder degraded(Health.Builder builder) {
        return builder.status("DEGRADED");
    }
}
