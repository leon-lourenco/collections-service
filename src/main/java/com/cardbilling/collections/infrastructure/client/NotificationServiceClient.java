package com.cardbilling.collections.infrastructure.client;

import com.cardbilling.collections.application.port.NotificationPort;
import com.cardbilling.collections.domain.NotificationRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Outbound call to {@code notification-service}'s {@code POST /notifications}, behind its own
 * {@code notification-request} circuit breaker and retry instance.
 *
 * <p>That service returns {@code 202} the instant the notification and its outbox row are written,
 * so a success here means "durably requested", not "delivered" — which is exactly the guarantee
 * the legacy did not have and the reason this call can be retried without risking a duplicate.
 */
@Component
public class NotificationServiceClient implements NotificationPort {

    private final RestClient restClient;

    public NotificationServiceClient(@Qualifier("notificationServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @Retry(name = "notification-request")
    @CircuitBreaker(name = "notification-request")
    public void requestNotification(NotificationRequest request) {
        restClient
                .post()
                .uri("/notifications")
                .body(NotificationRequestPayload.from(request))
                .retrieve()
                .toBodilessEntity();
    }
}
