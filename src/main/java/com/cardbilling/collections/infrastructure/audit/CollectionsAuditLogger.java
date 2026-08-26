package com.cardbilling.collections.infrastructure.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Audit logger for recording collections business events.
 */
@Component
public class CollectionsAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(CollectionsAuditLogger.class);

    public void logCollectionsRunStarted(String runDate, int invoiceCount) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "COLLECTIONS_RUN_STARTED");
        event.put("runDate", runDate);
        event.put("invoiceCount", invoiceCount);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Collections run started", event);
    }

    public void logEscalationApplied(Long invoiceId, String stage, int daysOverdue) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "ESCALATION_APPLIED");
        event.put("invoiceId", invoiceId);
        event.put("stage", stage);
        event.put("daysOverdue", daysOverdue);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Escalation applied", event);
    }

    public void logInterestCalculated(Long invoiceId, long feeCents, long dailyInterestCents) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "INTEREST_CALCULATED");
        event.put("invoiceId", invoiceId);
        event.put("feeCents", feeCents);
        event.put("dailyInterestCents", dailyInterestCents);
        event.put("totalCents", feeCents + dailyInterestCents);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Interest calculated", event);
    }

    public void logBillingServiceUnavailable(String reason) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "BILLING_SERVICE_UNAVAILABLE");
        event.put("reason", reason);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.warn("Audit: Billing service unavailable", event);
    }

    public void logCollectionsRunCompleted(String runDate, int processedCount, int failedCount) {
        Map<String, Object> event = new HashMap<>();
        event.put("action", "COLLECTIONS_RUN_COMPLETED");
        event.put("runDate", runDate);
        event.put("processedCount", processedCount);
        event.put("failedCount", failedCount);
        event.put("timestamp", Instant.now().toString());
        event.put("traceId", MDC.get("traceId"));

        log.info("Audit: Collections run completed", event);
    }
}
