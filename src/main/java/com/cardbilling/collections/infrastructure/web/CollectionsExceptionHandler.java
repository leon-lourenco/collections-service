package com.cardbilling.collections.infrastructure.web;

import com.cardbilling.collections.domain.BillingServiceUnavailableException;
import com.cardbilling.collections.domain.InvalidEscalationStageException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this service's domain exceptions onto RFC 7807 {@code application/problem+json} responses,
 * using Spring's built-in {@link ProblemDetail} — no extra library, and no generic 500 with a
 * stack trace leaking out of any endpoint here.
 */
@RestControllerAdvice
public class CollectionsExceptionHandler {

    static final String PROBLEM_TYPE_BASE = "https://cardbilling.example/problems/";

    private static final Logger log = LoggerFactory.getLogger(CollectionsExceptionHandler.class);

    /**
     * 503 rather than 500: nothing is wrong with this service or the request, the data it needs
     * simply is not obtainable right now. {@code Retry-After} says the request is worth repeating,
     * which is exactly true once the circuit breaker's wait window elapses.
     */
    @ExceptionHandler(BillingServiceUnavailableException.class)
    public ProblemDetail handleBillingServiceUnavailable(BillingServiceUnavailableException e) {
        log.error("Collections run for {} failed: no live and no cached overdue-invoice set", e.asOf(), e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setType(URI.create(PROBLEM_TYPE_BASE + "billing-service-unavailable"));
        problem.setTitle("billing-service unavailable");
        problem.setProperty("asOf", e.asOf().toString());
        return problem;
    }

    /**
     * 422 rather than 400: the request itself was well-formed, but the invoice data behind it
     * cannot be reconciled with the escalation rules — an invoice that is not actually overdue.
     */
    @ExceptionHandler(InvalidEscalationStageException.class)
    public ProblemDetail handleInvalidEscalationStage(InvalidEscalationStageException e) {
        log.error("Escalation stage could not be determined", e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
        problem.setType(URI.create(PROBLEM_TYPE_BASE + "invalid-escalation-stage"));
        problem.setTitle("Invalid escalation stage");
        problem.setProperty("daysOverdue", e.daysOverdue());
        return problem;
    }
}
