package com.cardbilling.collections.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The D+5 / D+15 / D+30 thresholds, pinned against {@code card-billing-legacy}'s
 * {@code DelinquencyJob.stageFor}. These boundaries decide when a customer gets a formal notice,
 * so an off-by-one here is a real-world consequence, not a rounding detail.
 */
class EscalationStageTest {

    @ParameterizedTest(name = "{0} days overdue reaches {1}")
    @CsvSource({
        "5, REMINDER_D5",
        "6, REMINDER_D5",
        "14, REMINDER_D5",
        "15, REMINDER_D15",
        "16, REMINDER_D15",
        "29, REMINDER_D15",
        "30, FORMAL_NOTICE_D30",
        "31, FORMAL_NOTICE_D30",
        "365, FORMAL_NOTICE_D30"
    })
    void reaches_the_stage_its_lateness_earns(long daysOverdue, EscalationStage expected) {
        assertThat(EscalationStage.forDaysOverdue(daysOverdue)).contains(expected);
    }

    @ParameterizedTest(name = "{0} days overdue has not reached any stage yet")
    @ValueSource(longs = {0, 1, 2, 3, 4})
    void has_no_stage_before_the_first_threshold(long daysOverdue) {
        assertThat(EscalationStage.forDaysOverdue(daysOverdue)).isEmpty();
    }

    @Test
    @DisplayName("an invoice that is not overdue at all is a contract violation, not an empty stage")
    void rejects_an_invoice_that_is_not_overdue() {
        assertThatThrownBy(() -> EscalationStage.forDaysOverdue(-1))
                .isInstanceOf(InvalidEscalationStageException.class)
                .hasMessageContaining("-1");
    }

    @Test
    void exposes_its_own_threshold() {
        assertThat(EscalationStage.REMINDER_D5.minimumDaysOverdue()).isEqualTo(5);
        assertThat(EscalationStage.REMINDER_D15.minimumDaysOverdue()).isEqualTo(15);
        assertThat(EscalationStage.FORMAL_NOTICE_D30.minimumDaysOverdue()).isEqualTo(30);
    }

    @Test
    @DisplayName("stage names are notification-service's contract, not an internal label")
    void keeps_the_legacy_stage_names() {
        // notification-service deduplicates on (invoiceId, stage) using these exact strings, and
        // card-billing-legacy's Notification.Stage used them too. Renaming one silently splits a
        // customer's escalation history in two.
        assertThat(EscalationStage.values())
                .extracting(Enum::name)
                .containsExactly("REMINDER_D5", "REMINDER_D15", "FORMAL_NOTICE_D30");
    }

    @Test
    void returns_an_empty_optional_rather_than_null() {
        Optional<EscalationStage> stage = EscalationStage.forDaysOverdue(0);
        assertThat(stage).isNotNull().isEmpty();
    }
}
