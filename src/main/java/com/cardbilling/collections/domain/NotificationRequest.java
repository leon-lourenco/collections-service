package com.cardbilling.collections.domain;

import java.util.Objects;

/**
 * A request for {@code notification-service} to notify a customer that an invoice has reached an
 * escalation stage. Matches that service's {@code POST /notifications} body exactly —
 * {@code {customerId, invoiceId, channel, stage}}.
 *
 * <p>There is deliberately no idempotency guard on this side. The legacy checked
 * {@code notificationRepository.existsByInvoiceAndStage} before publishing, which only worked
 * because the delinquency job and the notification table shared one database. {@code
 * notification-service} now enforces uniqueness on {@code (invoiceId, stage)} itself, so a rerun
 * of the same day is harmless without this service having to remember anything.
 */
public record NotificationRequest(
        String customerId, String invoiceId, NotificationChannel channel, EscalationStage stage) {

    public NotificationRequest {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(invoiceId, "invoiceId must not be null");
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
    }

    public static NotificationRequest forEscalation(
            OverdueInvoice invoice, EscalationStage stage, NotificationChannel channel) {
        return new NotificationRequest(invoice.customerId(), invoice.invoiceId(), channel, stage);
    }
}
