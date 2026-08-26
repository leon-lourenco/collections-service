package com.cardbilling.collections.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cardbilling.collections.application.port.InterestPort;
import com.cardbilling.collections.application.port.NotificationPort;
import com.cardbilling.collections.application.port.OverdueInvoicePort;
import com.cardbilling.collections.domain.BillingServiceUnavailableException;
import com.cardbilling.collections.domain.CollectionsRunSummary;
import com.cardbilling.collections.domain.EscalationStage;
import com.cardbilling.collections.domain.Freshness;
import com.cardbilling.collections.domain.InterestCalculation;
import com.cardbilling.collections.domain.Money;
import com.cardbilling.collections.domain.NotificationChannel;
import com.cardbilling.collections.domain.NotificationRequest;
import com.cardbilling.collections.domain.OverdueInvoice;
import com.cardbilling.collections.domain.OverdueInvoiceSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The merged run, with hand-written fakes rather than a mocking framework: these ports have three
 * methods between them, and a fake that records what it was asked to do reads better in the
 * assertions than a stack of verify calls. No Spring context is involved.
 */
class RunCollectionsUseCaseTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 25);

    private final RecordingInterestPort interest = new RecordingInterestPort();
    private final RecordingNotificationPort notifications = new RecordingNotificationPort();

    @Test
    @DisplayName("accrues interest and escalates every overdue invoice in one pass")
    void runs_both_legacy_jobs_over_a_single_fetch() {
        StubOverdueInvoicePort invoices = new StubOverdueInvoicePort(OverdueInvoiceSnapshot.live(
                List.of(overdue("inv-1", 5, null), overdue("inv-2", 30, TODAY.minusDays(1)))));

        CollectionsRunSummary summary = useCase(invoices).run(TODAY);

        // The whole reason the two legacy jobs are merged: the overdue set is fetched once.
        assertThat(invoices.calls).isEqualTo(1);
        assertThat(summary.overdueInvoices()).isEqualTo(2);
        assertThat(summary.interestApplied()).isEqualTo(2);
        assertThat(summary.notificationsRequested()).isEqualTo(2);
        assertThat(summary.failures()).isZero();
        assertThat(interest.applied).containsOnlyKeys("inv-1", "inv-2");
        assertThat(notifications.requests)
                .extracting(NotificationRequest::stage)
                .containsExactly(EscalationStage.REMINDER_D5, EscalationStage.FORMAL_NOTICE_D30);
    }

    @Test
    @DisplayName("the flat late fee is charged only on an invoice that has never accrued")
    void applies_the_legacy_interest_rule_per_invoice() {
        StubOverdueInvoicePort invoices = new StubOverdueInvoicePort(OverdueInvoiceSnapshot.live(
                List.of(overdue("never-accrued", 6, null), overdue("accrued-before", 6, TODAY.minusDays(1)))));

        useCase(invoices).run(TODAY);

        assertThat(interest.applied.get("never-accrued").lateFee().cents()).isEqualTo(2_000L);
        assertThat(interest.applied.get("accrued-before").lateFee().isZero()).isTrue();
        assertThat(interest.applied.get("accrued-before").dailyInterest().cents()).isEqualTo(1_000L);
        assertThat(interest.applied.get("never-accrued").accrualDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("an invoice already accrued today is skipped without a call")
    void does_not_re_accrue_an_invoice_on_the_same_day() {
        StubOverdueInvoicePort invoices =
                new StubOverdueInvoicePort(OverdueInvoiceSnapshot.live(List.of(overdue("inv-1", 10, TODAY))));

        CollectionsRunSummary summary = useCase(invoices).run(TODAY);

        assertThat(interest.applied).isEmpty();
        assertThat(summary.interestAlreadyAccrued()).isEqualTo(1);
        assertThat(summary.interestApplied()).isZero();
        // Escalation still happens: the notification for a stage is a separate concern from
        // whether today's interest has already been charged.
        assertThat(summary.notificationsRequested()).isEqualTo(1);
    }

    @Test
    @DisplayName("an invoice under five days late accrues interest but is not escalated yet")
    void does_not_escalate_below_the_first_stage() {
        StubOverdueInvoicePort invoices = new StubOverdueInvoicePort(
                OverdueInvoiceSnapshot.live(List.of(overdue("inv-1", 4, null), overdue("inv-2", 0, null))));

        CollectionsRunSummary summary = useCase(invoices).run(TODAY);

        assertThat(summary.interestApplied()).isEqualTo(2);
        assertThat(summary.notificationsRequested()).isZero();
        assertThat(summary.belowFirstEscalationStage()).isEqualTo(2);
        assertThat(notifications.requests).isEmpty();
    }

    @Test
    @DisplayName("one invoice failing does not abandon the rest of the run")
    void isolates_a_per_invoice_failure() {
        // The legacy ran the whole loop in one @Transactional method, so a failure on the last
        // invoice rolled back every invoice before it. There is no shared transaction here.
        interest.failFor("inv-2");
        StubOverdueInvoicePort invoices = new StubOverdueInvoicePort(OverdueInvoiceSnapshot.live(List.of(
                overdue("inv-1", 5, null), overdue("inv-2", 5, null), overdue("inv-3", 5, null))));

        CollectionsRunSummary summary = useCase(invoices).run(TODAY);

        assertThat(summary.failures()).isEqualTo(1);
        assertThat(summary.interestApplied()).isEqualTo(2);
        assertThat(interest.applied).containsOnlyKeys("inv-1", "inv-3");
        // inv-2 never reached escalation, but inv-3 - queued after it - still did.
        assertThat(notifications.requests)
                .extracting(NotificationRequest::invoiceId)
                .containsExactly("inv-1", "inv-3");
    }

    @Test
    @DisplayName("the run reports that it acted on stale cached data")
    void carries_the_snapshot_freshness_into_the_summary() {
        StubOverdueInvoicePort invoices = new StubOverdueInvoicePort(
                new OverdueInvoiceSnapshot(List.of(overdue("inv-1", 5, null)), Freshness.CACHED_STALE));

        CollectionsRunSummary summary = useCase(invoices).run(TODAY);

        assertThat(summary.freshness()).isEqualTo(Freshness.CACHED_STALE);
        assertThat(summary.degraded()).isTrue();
    }

    @Test
    void reports_a_live_run_as_not_degraded() {
        StubOverdueInvoicePort invoices =
                new StubOverdueInvoicePort(OverdueInvoiceSnapshot.live(List.of(overdue("inv-1", 5, null))));

        assertThat(useCase(invoices).run(TODAY).degraded()).isFalse();
    }

    @Test
    @DisplayName("a run that cannot get an invoice set at all fails outright")
    void propagates_an_unavailable_billing_service() {
        StubOverdueInvoicePort invoices = StubOverdueInvoicePort.unavailable();

        assertThatThrownBy(() -> useCase(invoices).run(TODAY))
                .isInstanceOf(BillingServiceUnavailableException.class);
        assertThat(interest.applied).isEmpty();
        assertThat(notifications.requests).isEmpty();
    }

    @Test
    @DisplayName("configuring both channels sends one notification per channel per stage")
    void honours_the_configured_channel_list() {
        StubOverdueInvoicePort invoices =
                new StubOverdueInvoicePort(OverdueInvoiceSnapshot.live(List.of(overdue("inv-1", 15, null))));

        CollectionsRunSummary summary = new RunCollectionsUseCase(
                        invoices,
                        interest,
                        notifications,
                        List.of(NotificationChannel.EMAIL, NotificationChannel.SMS))
                .run(TODAY);

        assertThat(summary.notificationsRequested()).isEqualTo(2);
        assertThat(notifications.requests)
                .extracting(NotificationRequest::channel)
                .containsExactly(NotificationChannel.EMAIL, NotificationChannel.SMS);
    }

    @Test
    void carries_the_customer_through_to_the_notification() {
        StubOverdueInvoicePort invoices =
                new StubOverdueInvoicePort(OverdueInvoiceSnapshot.live(List.of(overdue("inv-9", 20, null))));

        useCase(invoices).run(TODAY);

        NotificationRequest request = notifications.requests.getFirst();
        assertThat(request.customerId()).isEqualTo("cus-inv-9");
        assertThat(request.invoiceId()).isEqualTo("inv-9");
        assertThat(request.stage()).isEqualTo(EscalationStage.REMINDER_D15);
        assertThat(request.channel()).isEqualTo(NotificationChannel.EMAIL);
    }

    private RunCollectionsUseCase useCase(OverdueInvoicePort invoices) {
        return new RunCollectionsUseCase(invoices, interest, notifications, List.of(NotificationChannel.EMAIL));
    }

    private static OverdueInvoice overdue(String id, int daysOverdue, LocalDate lastAccrual) {
        return new OverdueInvoice(
                id, "cus-" + id, Money.ofCents(100_000L, "BRL"), TODAY.minusDays(daysOverdue), lastAccrual);
    }

    private static final class StubOverdueInvoicePort implements OverdueInvoicePort {

        private final OverdueInvoiceSnapshot snapshot;
        private int calls;

        private StubOverdueInvoicePort(OverdueInvoiceSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        static StubOverdueInvoicePort unavailable() {
            return new StubOverdueInvoicePort(null);
        }

        @Override
        public OverdueInvoiceSnapshot findOverdueAsOf(LocalDate asOf) {
            calls++;
            if (snapshot == null) {
                throw new BillingServiceUnavailableException(asOf, new IllegalStateException("stub"));
            }
            return snapshot;
        }
    }

    private static final class RecordingInterestPort implements InterestPort {

        private final Map<String, InterestCalculation> applied = new ConcurrentHashMap<>();
        private final Set<String> failing = new HashSet<>();

        void failFor(String invoiceId) {
            failing.add(invoiceId);
        }

        @Override
        public void applyInterest(String invoiceId, InterestCalculation calculation) {
            if (failing.contains(invoiceId)) {
                throw new IllegalStateException("billing-service rejected " + invoiceId);
            }
            applied.put(invoiceId, calculation);
        }
    }

    private static final class RecordingNotificationPort implements NotificationPort {

        private final List<NotificationRequest> requests = new ArrayList<>();

        @Override
        public void requestNotification(NotificationRequest request) {
            requests.add(request);
        }
    }
}
