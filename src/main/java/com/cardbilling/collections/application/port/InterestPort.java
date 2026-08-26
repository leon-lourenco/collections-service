package com.cardbilling.collections.application.port;

import com.cardbilling.collections.domain.InterestCalculation;

/**
 * Applies a computed late fee and daily interest to an invoice. This service works out
 * <em>what</em> is owed; {@code billing-service} owns what applying it means and enforces the
 * one-accrual-per-day rule on its own side.
 */
public interface InterestPort {

    void applyInterest(long invoiceId, InterestCalculation calculation);
}
