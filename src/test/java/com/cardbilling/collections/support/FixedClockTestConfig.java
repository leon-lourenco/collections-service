package com.cardbilling.collections.support;

import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the application's system clock with one the test drives. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockTestConfig {

    public static final Instant START = Instant.parse("2026-08-25T09:00:00Z");

    @Bean
    @Primary
    public Clock clock() {
        return MutableClock.at(START);
    }
}
