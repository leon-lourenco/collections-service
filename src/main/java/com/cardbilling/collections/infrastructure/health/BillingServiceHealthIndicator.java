package com.cardbilling.collections.infrastructure.health;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Health indicator for downstream billing-service dependency.
 */
@Component
public class BillingServiceHealthIndicator extends AbstractHealthIndicator {

    private final RestClient restClient;

    public BillingServiceHealthIndicator(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.baseUrl("http://billing-service:8081").build();
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
                builder.down().withDetail("service", "billing-service");
            }
        } catch (Exception ex) {
            builder.down().withException(ex);
        }
    }
}
