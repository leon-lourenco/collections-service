package com.cardbilling.collections.domain;

/**
 * Delivery channel for an escalation notification. Same two values as the legacy's
 * {@code Notification.Channel}; {@code notification-service} owns what actually happens on each.
 */
public enum NotificationChannel {
    EMAIL,
    SMS
}
