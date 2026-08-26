package com.cardbilling.collections.infrastructure.scheduling;

import com.cardbilling.collections.application.RunCollectionsUseCase;
import com.cardbilling.collections.domain.BillingServiceUnavailableException;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The daily run. Same use case the trigger endpoint calls — the schedule is an adapter, not a
 * second implementation, so there is no way for the two to drift apart.
 *
 * <p>A failed scheduled run is logged and swallowed rather than rethrown: an uncaught exception in
 * a {@code @Scheduled} method is invisible unless someone is watching the log anyway, and the next
 * day's run picks up everything this one missed because both downstream calls are idempotent.
 */
@Component
@ConditionalOnProperty(name = "collections.schedule.enabled", havingValue = "true", matchIfMissing = true)
public class DailyCollectionsScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyCollectionsScheduler.class);

    private final RunCollectionsUseCase runCollections;
    private final Clock clock;

    public DailyCollectionsScheduler(RunCollectionsUseCase runCollections, Clock clock) {
        this.runCollections = runCollections;
        this.clock = clock;
    }

    @Scheduled(cron = "${collections.schedule.cron}", zone = "${collections.schedule.zone}")
    public void runDailyCollections() {
        LocalDate today = LocalDate.now(clock);
        try {
            log.info("Scheduled collections run starting for {}", today);
            runCollections.run(today);
        } catch (BillingServiceUnavailableException e) {
            log.error("Scheduled collections run for {} could not start", today, e);
        } catch (RuntimeException e) {
            log.error("Scheduled collections run for {} failed unexpectedly", today, e);
        }
    }
}
