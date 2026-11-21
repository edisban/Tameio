package com.tameio.platform.demo;

import com.tameio.platform.validation.AfmValidator;
import com.tameio.platform.validation.Iban;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoYearTest {

    private static final LocalDate END = LocalDate.of(2026, 11, 20);

    @ParameterizedTest
    @EnumSource(DemoYear.Profile.class)
    void aYearNeverOverdrawsAndEveryExpenseAddsUp(DemoYear.Profile profile) {
        // Whatever the day the stack starts and whatever the seed
        for (int seed = 0; seed < 60; seed++) {
            LocalDate end = END.plusDays(seed * 6L);
            DemoYear.Year year = DemoYear.plan(profile, end, seed);
            BigDecimal balance = year.openingCapital();
            for (DemoYear.Movement m : year.movements()) {
                balance = m.flow() == DemoYear.Flow.INCOME ? balance.add(m.total()) : balance.subtract(m.total());
                assertThat(balance).as("%s seed %d on %s", profile, seed, m.date()).isNotNegative();
            }
        }
    }

    @ParameterizedTest
    @EnumSource(DemoYear.Profile.class)
    void movementsAddUpAndTheBusinessMakesMoney(DemoYear.Profile profile) {
        DemoYear.Year plan = DemoYear.plan(profile, END, 7);
        List<DemoYear.Movement> year = plan.movements();

        BigDecimal balance = plan.openingCapital();
        for (DemoYear.Movement m : year) {
            balance = m.flow() == DemoYear.Flow.INCOME ? balance.add(m.total()) : balance.subtract(m.total());
            assertThat(m.net().add(m.vat())).isEqualByComparingTo(m.total());
            assertThat(m.total()).isPositive();
            assertThat(m.date()).isBetween(END.minusMonths(12).plusDays(1), END);
        }
        // A business that makes money over the year, so the overview has something to celebrate
        assertThat(balance).isGreaterThan(plan.openingCapital());
        assertThat(year).filteredOn(m -> m.flow() == DemoYear.Flow.EXPENSE).extracting(DemoYear.Movement::category)
            .contains("RENT", "EQUIPMENT");
    }

    @Test
    void theSameSeedGivesTheSameYear() {
        assertThat(DemoYear.plan(DemoYear.Profile.CAFE, END, 3)).isEqualTo(DemoYear.plan(DemoYear.Profile.CAFE, END, 3));
        assertThat(DemoYear.plan(DemoYear.Profile.CAFE, END, 3).movements()).isNotEqualTo(DemoYear.plan(DemoYear.Profile.CAFE, END, 4).movements());
    }

    @Test
    void suppliersHaveValidAfmsAndIbans() {
        for (DemoYear.Movement m : DemoYear.plan(DemoYear.Profile.BAKERY, END, 1).movements()) {
            assertThat(Iban.of("GR", m.counterpartyIban().substring(4))).isEqualTo(m.counterpartyIban());
            if (m.counterpartyAfm() != null) {
                assertThat(AfmValidator.isValid(m.counterpartyAfm())).as(m.counterparty()).isTrue();
            }
        }
    }

    @Test
    void theIslandTavernaIsShutInWinter() {
        assertThat(DemoYear.plan(DemoYear.Profile.TAVERNA, END, 1).movements())
            .filteredOn(m -> m.flow() == DemoYear.Flow.INCOME && m.date().getMonthValue() <= 2)
            .isEmpty();
    }
}
