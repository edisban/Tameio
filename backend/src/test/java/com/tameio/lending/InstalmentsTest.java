package com.tameio.lending;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class InstalmentsTest {

    @Test
    void quotesTheAnnuityInstalment() {
        // 10.000 € at 7% over 36 months: 308,77 € a month, as any loan calculator gives
        assertThat(Instalments.monthly(new BigDecimal("10000"), new BigDecimal("0.07"), 36)).isEqualByComparingTo("308.77");
    }

    @Test
    void anInterestFreeLoanIsTheAmountSplitEvenly() {
        assertThat(Instalments.monthly(new BigDecimal("1200"), BigDecimal.ZERO, 12)).isEqualByComparingTo("100.00");
    }
}
