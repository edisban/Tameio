package com.tameio.banking;

import com.tameio.identity.DemoBusinessReady;
import com.tameio.platform.demo.DemoYear;
import com.tameio.platform.validation.Iban;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Gives a demo business a current account and a year of history: an opening deposit, then every
 * movement of its {@link DemoYear}, booked through the ledger like any other money, dated on the day
 * it happened. The trial balance stays at zero.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class DemoBanking {

    private static final ZoneId ATHENS = ZoneId.of("Europe/Athens");
    private static final String SYSTEM = "demo";

    private final BankAccountRepository accounts;
    private final Ledger ledger;

    @EventListener
    void on(DemoBusinessReady demo) {
        String iban = Iban.of("GR", BankingService.BANK_CODE + BankingService.BRANCH + "%016d".formatted(accounts.nextAccountNumber()));
        BankAccount account = accounts.save(BankAccount.openCurrent(demo.businessId(), demo.name(), iban, "Λογαριασμός όψεως"));
        var settlement = ledger.internal(InternalAccount.SETTLEMENT);

        DemoYear.Year plan = DemoYear.plan(demo.profile(), demo.end(), demo.seed());
        List<DemoYear.Movement> year = plan.movements();
        LocalDate opening = demo.end().minusMonths(12);
        BigDecimal capital = plan.openingCapital();
        ledger.book(EntryKind.INCOMING_PAYMENT, "Opening capital", "Κεφάλαιο έναρξης", SYSTEM, null, List.of(
            new Ledger.Leg(settlement, capital.negate(), demo.name(), iban),
            new Ledger.Leg(account.getId(), capital, "Ιδρυτής", null)), opening.atTime(9, 0).atZone(ATHENS).toInstant());

        int minute = 0;
        for (DemoYear.Movement m : year) {
            // Spread through the working day, so statements show different times
            var at = m.date().atTime(LocalTime.of(9 + (minute % 9), (minute * 7) % 60)).atZone(ATHENS).toInstant();
            minute++;
            if (m.flow() == DemoYear.Flow.INCOME) {
                ledger.book(EntryKind.INCOMING_PAYMENT, "Incoming payment from " + m.counterparty(), m.reference(), SYSTEM, null, List.of(
                    new Ledger.Leg(settlement, m.total().negate(), demo.name(), iban),
                    new Ledger.Leg(account.getId(), m.total(), m.counterparty(), m.counterpartyIban())), at);
            } else {
                ledger.book(EntryKind.SEPA_PAYMENT, "SEPA payment to " + m.counterparty(), m.reference(), SYSTEM, null, List.of(
                    new Ledger.Leg(account.getId(), m.total().negate(), m.counterparty(), m.counterpartyIban()),
                    new Ledger.Leg(settlement, m.total(), demo.name(), iban)), at);
            }
        }
        log.info("Demo account {} for {}: {} movements", iban, demo.name(), year.size() + 1);
    }
}
