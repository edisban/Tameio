package com.tameio.documents;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A person confirmed a document's values. Published each time, so a second review replaces the first
 * wherever these values were used, such as in the books.
 */
public record DocumentReviewed(UUID documentId, UUID businessId, String issuerAfm, LocalDate issueDate,
                               BigDecimal netAmount, BigDecimal vatAmount, BigDecimal totalAmount,
                               ExpenseCategory category) {
}
