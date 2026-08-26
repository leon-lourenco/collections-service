package com.cardbilling.collections.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Custom metrics for collections service business operations.
 */
@Component
public class CollectionsMetrics {

    private final MeterRegistry meterRegistry;

    public CollectionsMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordCollectionsRun(int invoiceCount, int escalatedCount) {
        Counter.builder("collections.run")
            .description("Number of collection runs executed")
            .register(meterRegistry)
            .increment();

        meterRegistry.gauge("collections.run.invoices.count", invoiceCount);
        meterRegistry.gauge("collections.run.escalated.count", escalatedCount);
    }

    public void recordEscalationStage(String stage, int invoiceCount) {
        Counter.builder("escalation.stage")
            .description("Number of invoices escalated")
            .tag("stage", stage)
            .register(meterRegistry)
            .increment(invoiceCount);
    }

    public void recordBillingServiceCall(boolean success, long latencyMs) {
        String outcome = success ? "success" : "failure";
        Counter.builder("billing.service.calls")
            .description("Calls to billing-service")
            .tag("outcome", outcome)
            .register(meterRegistry)
            .increment();

        meterRegistry.timer("billing.service.latency", "outcome", outcome)
            .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordCacheOperation(String operation, boolean hit) {
        String type = hit ? "hit" : "miss";
        Counter.builder("cache.operations")
            .description("Cache operations")
            .tag("operation", operation)
            .tag("result", type)
            .register(meterRegistry)
            .increment();
    }

    public void recordCircuitBreakerStateChange(String state) {
        meterRegistry.gauge("circuit.breaker.state",
            Map.of("state", state),
            state.equals("CLOSED") ? 0 : (state.equals("OPEN") ? 1 : 2));
    }

    public void recordNotificationPublished(int count, long latencyMs) {
        Counter.builder("notifications.published")
            .description("Notifications published to notification-service")
            .register(meterRegistry)
            .increment(count);

        meterRegistry.timer("notification.service.latency")
            .record(latencyMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
