package com.tameio.lending;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/** @param annualRate the nominal yearly interest rate quoted on every application, such as 0.07 for 7% */
@Validated
@ConfigurationProperties("tameio.lending")
record LendingProperties(@NotNull @DecimalMin("0.00") @DecimalMax("0.30") BigDecimal annualRate) {
}
