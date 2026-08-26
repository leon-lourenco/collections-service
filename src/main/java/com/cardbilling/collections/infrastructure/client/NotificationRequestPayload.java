package com.cardbilling.collections.infrastructure.client;

import com.cardbilling.collections.domain.NotificationRequest;

/**
 * Body of {@code notification-service}'s {@code POST /notifications} —
 * {@code {customerId, invoiceId, channel, stage}}. That service deduplicates on
 * {@code (invoiceId, stage)} and returns the existing record for a repeat, so this call is safe to
 * retry.
 */
record NotificationRequestPayload(String customerId, String invoiceId, String channel, String stage) {

    static NotificationRequestPayload from(NotificationRequest request) {
        return new NotificationRequestPayload(
                request.customerId(), request.invoiceId(), request.channel().name(), request.stage().name());
    }
}
