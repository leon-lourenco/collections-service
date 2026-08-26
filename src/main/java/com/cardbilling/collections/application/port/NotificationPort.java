package com.cardbilling.collections.application.port;

import com.cardbilling.collections.domain.NotificationRequest;

/**
 * Requests an escalation notification. {@code notification-service} deduplicates on
 * {@code (invoiceId, stage)}, so a repeated request for a stage already requested is safe.
 */
public interface NotificationPort {

    void requestNotification(NotificationRequest request);
}
