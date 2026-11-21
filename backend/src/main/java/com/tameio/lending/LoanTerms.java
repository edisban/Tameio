package com.tameio.lending;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a business asks for. The limits are the product's: business loans from 1.000 € to 500.000 €,
 * over six months to seven years.
 *
 * @param accountId the business's account the loan is paid into
 */
public record LoanTerms(
    @NotNull(message = "Choose the account the loan is paid into") UUID accountId,
    @NotNull(message = "The amount is required")
    @DecimalMin(value = "1000.00", message = "At least 1,000")
    @DecimalMax(value = "500000.00", message = "At most 500,000")
    @Digits(integer = 6, fraction = 2, message = "At most two decimals") BigDecimal amount,
    @NotNull(message = "The term is required")
    @Min(value = 6, message = "At least 6 months") @Max(value = 84, message = "At most 84 months") Integer termMonths,
    @NotNull(message = "Choose a purpose") LoanPurpose purpose,
    @Size(max = 500, message = "At most 500 characters") String description) {

    public LoanTerms {
        description = description == null || description.isBlank() ? null : description.strip();
    }
}
