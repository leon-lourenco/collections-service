package com.cardbilling.collections.infrastructure.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "Now" as an injectable bean rather than a static call. Cache freshness is the whole resilience
 * story here, and a test that has to sleep for sixty seconds to prove a fallback works is a test
 * nobody runs.
 */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
