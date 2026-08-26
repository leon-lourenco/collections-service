package com.cardbilling.collections.domain;

import java.util.Optional;

/**
 * How far collections has escalated on an invoice, keyed to how many days late it is: a first
 * reminder at D+5, a second at D+15, a formal notice at D+30. Same three thresholds and same
 * names as {@code card-billing-legacy}'s {@code Notification.Stage} — this is the vocabulary
 * {@code notification-service} matches on, so a rename here is a contract change, not a
 * refactor.
 *
 * <p>An invoice fewer than five days late has no stage at all yet, which is why
 * {@link #forDaysOverdue(long)} returns an {@link Optional} rather than a nullable stage the way
 * the legacy job did.
 */
public enum EscalationStage {

    REMINDER_D5(5),
    REMINDER_D15(15),
    FORMAL_NOTICE_D30(30);

    private final int minimumDaysOverdue;

    EscalationStage(int minimumDaysOverdue) {
        this.minimumDaysOverdue = minimumDaysOverdue;
    }

    public int minimumDaysOverdue() {
        return minimumDaysOverdue;
    }

    /**
     * The stage an invoice this many days late has reached, or empty if it has not reached the
     * first one yet.
     *
     * @throws InvalidEscalationStageException if the invoice is not actually overdue — a negative
     *     day count means something upstream handed us an invoice that is not due yet, and
     *     silently treating that as "no stage" would hide the bug
     */
    public static Optional<EscalationStage> forDaysOverdue(long daysOverdue) {
        if (daysOverdue < 0) {
            throw new InvalidEscalationStageException(daysOverdue);
        }
        if (daysOverdue >= FORMAL_NOTICE_D30.minimumDaysOverdue) {
            return Optional.of(FORMAL_NOTICE_D30);
        }
        if (daysOverdue >= REMINDER_D15.minimumDaysOverdue) {
            return Optional.of(REMINDER_D15);
        }
        if (daysOverdue >= REMINDER_D5.minimumDaysOverdue) {
            return Optional.of(REMINDER_D5);
        }
        return Optional.empty();
    }
}
