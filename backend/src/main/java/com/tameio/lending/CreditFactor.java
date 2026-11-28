package com.tameio.lending;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/**
 * One measure the credit model read, and how much it moved the estimate (log-odds against an average applicant:
 * above zero raises the chance of default, below zero lowers it).
 */
@Embeddable
public record CreditFactor(
    @Column(nullable = false, length = 30) String measure,
    @Column(name = "measured", nullable = false, precision = 14, scale = 6) BigDecimal value,
    @Column(nullable = false, precision = 10, scale = 4) BigDecimal contribution) {
}
