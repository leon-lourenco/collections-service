package com.cardbilling.collections.domain;

/**
 * Raised when an escalation stage is asked for on an invoice that is not actually overdue. A
 * negative day count means {@code billing-service} returned an invoice that is not past due, or
 * the run date is before the invoice's due date — either way it is a contract violation upstream,
 * not a "no stage yet" case.
 */
public class InvalidEscalationStageException extends RuntimeException {

    private final long daysOverdue;

    public InvalidEscalationStageException(long daysOverdue) {
        super("Cannot determine an escalation stage for an invoice %d days past due".formatted(daysOverdue));
        this.daysOverdue = daysOverdue;
    }

    public long daysOverdue() {
        return daysOverdue;
    }
}
