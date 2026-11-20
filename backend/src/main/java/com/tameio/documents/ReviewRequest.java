package com.tameio.documents;

import com.tameio.platform.validation.ValidAfm;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The values a person confirms for a document, starting from what document AI read. Net plus VAT
 * must equal the total, to the cent: a receipt that does not add up is a typo, or a forgery.
 */
public record ReviewRequest(
    @NotBlank(message = "ΑΦΜ is required") @ValidAfm String issuerAfm,
    @NotNull(message = "Issue date is required") @PastOrPresent(message = "Issue date cannot be in the future") LocalDate issueDate,
    @NotNull(message = "Net amount is required") @DecimalMin(value = "0.00", message = "Net amount cannot be negative")
    @Digits(integer = 12, fraction = 2, message = "Net amount has at most two decimals") BigDecimal netAmount,
    @NotNull(message = "VAT is required") @DecimalMin(value = "0.00", message = "VAT cannot be negative")
    @Digits(integer = 12, fraction = 2, message = "VAT has at most two decimals") BigDecimal vatAmount,
    @NotNull(message = "Total is required") @DecimalMin(value = "0.01", message = "Total must be positive")
    @Digits(integer = 12, fraction = 2, message = "Total has at most two decimals") BigDecimal totalAmount,
    @NotNull(message = "Category is required") ExpenseCategory category) {

    public ReviewRequest {
        issuerAfm = issuerAfm == null ? null : issuerAfm.replaceAll("\\s+", "");
    }

    @AssertTrue(message = "Net amount plus VAT must equal the total")
    public boolean isBalanced() {
        // Missing amounts already have their own message
        return netAmount == null || vatAmount == null || totalAmount == null
            || netAmount.add(vatAmount).compareTo(totalAmount) == 0;
    }
}
