package com.cardbilling.collections.application;

import com.cardbilling.collections.application.port.InterestPort;
import com.cardbilling.collections.application.port.NotificationPort;
import com.cardbilling.collections.application.port.OverdueInvoicePort;
import com.cardbilling.collections.domain.CollectionsRunSummary;
import com.cardbilling.collections.domain.EscalationStage;
import com.cardbilling.collections.domain.InterestCalculation;
import com.cardbilling.collections.domain.NotificationChannel;
import com.cardbilling.collections.domain.NotificationRequest;
import com.cardbilling.collections.domain.OverdueInvoice;
import com.cardbilling.collections.domain.OverdueInvoiceSnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * One collections run: read every overdue invoice as of a date, accrue what it owes for the day,
 * and escalate it to the stage its lateness has reached.
 *
 * <p>This is the legacy's {@code InterestAccrualJob} and {@code DelinquencyJob} merged. They were
 * two components reading the identical query — {@code findByStatusNotAndDueDateBefore} — and
 * running back to back. Merging them means the overdue set is fetched once per run instead of
 * twice, which matters a great deal more now that fetching it is a network call rather than a
 * query against the same database.
 *
 * <p>A failure on one invoice does not abandon the rest of the run. In the legacy both jobs were
 * {@code @Transactional} over the whole loop, so one bad invoice rolled back every invoice
 * processed before it. There is no shared transaction to roll back here — each invoice's effects
 * land in {@code billing-service} and {@code notification-service} independently — so the honest
 * behaviour is to record the failure, keep going, and let the next run pick the invoice up again
 * (both downstream calls are idempotent, so re-running it is safe).
 */
@Service
public class RunCollectionsUseCase {

    private static final Logger log = LoggerFactory.getLogger(RunCollectionsUseCase.class);

    private final OverdueInvoicePort overdueInvoicePort;
    private final InterestPort interestPort;
    private final NotificationPort notificationPort;
    private final List<NotificationChannel> channels;

    public RunCollectionsUseCase(
            OverdueInvoicePort overdueInvoicePort,
            InterestPort interestPort,
            NotificationPort notificationPort,
            @Value("${collections.notification.channels}") List<NotificationChannel> channels) {
        this.overdueInvoicePort = overdueInvoicePort;
        this.interestPort = interestPort;
        this.notificationPort = notificationPort;
        this.channels = List.copyOf(channels);
    }

    public CollectionsRunSummary run(LocalDate asOf) {
        OverdueInvoiceSnapshot snapshot = overdueInvoicePort.findOverdueAsOf(asOf);
        log.info(
                "Collections run for {}: {} overdue invoice(s), source={}",
                asOf,
                snapshot.size(),
                snapshot.freshness());

        CollectionsRunSummary.Builder summary =
                CollectionsRunSummary.builder(asOf, snapshot.freshness(), snapshot.size());

        for (OverdueInvoice invoice : snapshot.invoices()) {
            try {
                accrueInterest(invoice, asOf, summary);
                escalate(invoice, asOf, summary);
            } catch (RuntimeException e) {
                summary.failure();
                log.warn("Collections run for {} could not process invoice {}", asOf, invoice.invoiceId(), e);
            }
        }

        CollectionsRunSummary result = summary.build();
        log.info("Collections run for {} finished: {}", asOf, result);
        return result;
    }

    private void accrueInterest(OverdueInvoice invoice, LocalDate asOf, CollectionsRunSummary.Builder summary) {
        // billing-service enforces one accrual per (invoiceId, accrualDate) itself, so this check
        // is not what makes a rerun safe - it just saves a network call for invoices this run has
        // already touched today. The legacy's lastInterestAccrualDate guard was the only guard;
        // here it is an optimisation on top of a server-side rule.
        if (invoice.alreadyAccruedOn(asOf)) {
            summary.interestAlreadyAccrued();
            return;
        }
        InterestCalculation calculation = invoice.interestFor(asOf);
        interestPort.applyInterest(invoice.invoiceId(), calculation);
        summary.interestApplied();
    }

    private void escalate(OverdueInvoice invoice, LocalDate asOf, CollectionsRunSummary.Builder summary) {
        Optional<EscalationStage> stage = EscalationStage.forDaysOverdue(invoice.daysOverdueAsOf(asOf));
        if (stage.isEmpty()) {
            summary.belowFirstEscalationStage();
            return;
        }
        for (NotificationChannel channel : channels) {
            notificationPort.requestNotification(NotificationRequest.forEscalation(invoice, stage.get(), channel));
            summary.notificationRequested();
        }
    }
}
