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
 * notification-service} now enforces uniqueness on {@code (invoiceId, stage, channel)} itself, so
 * a rerun of the same day is harmless without this service having to remember anything — and
 * because the channel is part of that key, the legacy's separate EMAIL and SMS notifications for
 * one stage are two distinct records rather than the second silently collapsing into the first.
 */
public record NotificationRequest(
        long customerId, long invoiceId, NotificationChannel channel, EscalationStage stage) {

    public NotificationRequest {
        Objects.requireNonNull(channel, "channel must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
    }

    public static NotificationRequest forEscalation(
            OverdueInvoice invoice, EscalationStage stage, NotificationChannel channel) {
        return new NotificationRequest(invoice.customerId(), invoice.invoiceId(), channel, stage);
    }
}
