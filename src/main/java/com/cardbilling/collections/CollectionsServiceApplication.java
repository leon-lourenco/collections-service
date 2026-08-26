package com.cardbilling.collections;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for {@code collections-service} — delinquency escalation and interest accrual,
 * merged into one service because both read the same overdue-invoice set at the same point in
 * the billing cycle.
 *
 * <p>This service owns no database. Every read and every write goes through {@code
 * billing-service}'s and {@code notification-service}'s APIs; Redis holds nothing but a
 * short-lived cache of another service's data.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CollectionsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectionsServiceApplication.class, args);
    }
}
