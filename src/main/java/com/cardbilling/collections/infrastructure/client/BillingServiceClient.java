package com.cardbilling.collections.infrastructure.client;

import com.cardbilling.collections.application.port.InterestPort;
import com.cardbilling.collections.domain.InterestCalculation;
import com.cardbilling.collections.domain.OverdueInvoice;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Outbound calls to {@code billing-service}, each behind its own named circuit breaker and retry
 * instance ({@code billing-overdue-invoices}, {@code billing-apply-interest}) so a struggling
 * overdue query cannot trip the breaker that guards interest application, or the reverse.
 *
 * <p>Nothing here falls back. When {@code billing-service} is unreachable or the breaker is open,
 * these methods throw and the caller decides what that means — {@link CachedOverdueInvoiceAdapter}
 * serves stale cache for the overdue query, while a failed interest call is recorded against that
 * one invoice and the run carries on. Putting a Resilience4j {@code fallbackMethod} here instead
 * would bury both of those decisions inside an AOP proxy.
 *
 * <p>This class implements {@link InterestPort} directly because applying interest needs no
 * decoration; the overdue query is exposed as a plain method for the caching adapter to wrap.
 */
@Component
public class BillingServiceClient implements InterestPort {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceClient.class);

    private final RestClient restClient;

    public BillingServiceClient(@Qualifier("billingServiceRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Retry(name = "billing-overdue-invoices")
    @CircuitBreaker(name = "billing-overdue-invoices")
    public List<OverdueInvoice> fetchOverdueAsOf(LocalDate asOf) {
        log.debug("Fetching overdue invoices from billing-service as of {}", asOf);
        OverdueInvoiceResponse[] body = restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/invoices/overdue")
                        .queryParam("asOf", asOf)
                        .build())
                .retrieve()
                .body(OverdueInvoiceResponse[].class);
        if (body == null) {
            return List.of();
        }
        return Arrays.stream(body).map(OverdueInvoiceResponse::toDomain).toList();
    }

    @Override
    @Retry(name = "billing-apply-interest")
    @CircuitBreaker(name = "billing-apply-interest")
    public void applyInterest(String invoiceId, InterestCalculation calculation) {
        restClient
                .post()
                .uri("/invoices/{id}/interest", invoiceId)
                .body(ApplyInterestRequest.from(calculation))
                .retrieve()
                .toBodilessEntity();
    }
}
