package com.tameio.lending;

import java.util.Map;
import java.util.Set;

/**
 * Where an application stands. The allowed moves are listed here and nowhere else:
 *
 * <pre>
 *   DRAFT → SUBMITTED → IN_REVIEW → RECOMMENDED → APPROVED → DISBURSED
 *                                              ↘ DECLINED
 * </pre>
 */
public enum LoanStatus {
    /** The business is still filling it in. */
    DRAFT,
    /** Sent to the bank, waiting for an analyst. */
    SUBMITTED,
    /** An analyst is assessing it. */
    IN_REVIEW,
    /** The analyst recommended approving or declining; a manager decides. */
    RECOMMENDED,
    APPROVED,
    DECLINED,
    /** The money is in the business's account. */
    DISBURSED;

    private static final Map<LoanStatus, Set<LoanStatus>> NEXT = Map.of(
        DRAFT, Set.of(SUBMITTED),
        SUBMITTED, Set.of(IN_REVIEW),
        IN_REVIEW, Set.of(RECOMMENDED),
        RECOMMENDED, Set.of(APPROVED, DECLINED),
        APPROVED, Set.of(DISBURSED),
        DECLINED, Set.of(),
        DISBURSED, Set.of());

    boolean canMoveTo(LoanStatus next) {
        return NEXT.get(this).contains(next);
    }
}
