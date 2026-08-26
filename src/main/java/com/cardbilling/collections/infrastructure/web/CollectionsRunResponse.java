package com.cardbilling.collections.infrastructure.web;

import com.cardbilling.collections.domain.CollectionsRunSummary;
import com.cardbilling.collections.domain.Freshness;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * What {@code POST /collections/run} reports back.
 *
 * <p>{@code invoiceSource} and {@code degraded} are here on purpose: an operator triggering a run
 * during a {@code billing-service} outage needs the response itself to say the run acted on cached
 * data, rather than having to go and read a log to find out.
 */
@Schema(description = "Outcome of one collections run")
public record CollectionsRunResponse(
        @Schema(description = "Date the run was evaluated against") LocalDate asOf,
        @Schema(description = "Where the overdue-invoice set came from") Freshness invoiceSource,
        @Schema(description = "True when the run acted on a cached set because billing-service was unreachable")
                boolean degraded,
        int overdueInvoices,
        @Schema(description = "Invoices that had interest applied on this run") int interestApplied,
        @Schema(description = "Invoices already accrued for this date, skipped") int interestAlreadyAccrued,
        @Schema(description = "Notification requests sent to notification-service") int notificationsRequested,
        @Schema(description = "Overdue invoices not yet at the D+5 stage") int belowFirstEscalationStage,
        @Schema(description = "Invoices this run could not process; the next run retries them") int failures) {

    public static CollectionsRunResponse from(CollectionsRunSummary summary) {
        return new CollectionsRunResponse(
                summary.asOf(),
                summary.freshness(),
                summary.degraded(),
                summary.overdueInvoices(),
                summary.interestApplied(),
                summary.interestAlreadyAccrued(),
                summary.notificationsRequested(),
                summary.belowFirstEscalationStage(),
                summary.failures());
    }
}
