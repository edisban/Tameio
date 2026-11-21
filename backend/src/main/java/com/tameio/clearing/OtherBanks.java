package com.tameio.clearing;

import com.tameio.platform.validation.Iban;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The accounts the simulated other banks hold, for Verification of Payee and for demos. The banks and
 * holders are fictional; bank codes 991 to 994 belong to no real Greek bank.
 */
final class OtherBanks {

    record Account(String iban, String holderName, String bank) {
    }

    static final List<Account> ACCOUNTS = List.of(
        account("991", "0001", 1001, "ΗΛΕΚΤΡΙΚΗ ΕΝΕΡΓΕΙΑ ΑΙΓΑΙΟΥ Α.Ε.", "Aegean Demo Bank"),
        account("991", "0001", 1002, "ΧΑΡΤΙΚΑ ΑΙΓΑΙΟΥ Ο.Ε.", "Aegean Demo Bank"),
        account("992", "0003", 2001, "ΤΗΛΕΠΙΚΟΙΝΩΝΙΕΣ ΑΣΤΕΡΙ Α.Ε.", "Olympus Demo Bank"),
        account("993", "0010", 3001, "ΜΕΤΑΦΟΡΙΚΗ ΘΕΣΣΑΛΙΑΣ Ι.Κ.Ε.", "Thessaly Demo Bank"),
        account("994", "0002", 4001, "ΓΕΩΡΓΙΟΥ ΝΙΚΟΛΑΟΣ", "Ionian Demo Bank"),
        // For demos of returns: closed (ends in 0000)
        account("992", "0003", 10000, "ΠΑΛΙΟ ΚΑΤΑΣΤΗΜΑ Ε.Π.Ε.", "Olympus Demo Bank"));

    private static final Map<String, Account> BY_IBAN = ACCOUNTS.stream().collect(Collectors.toMap(Account::iban, Function.identity()));

    private OtherBanks() {
    }

    static Optional<Account> find(String iban) {
        return Optional.ofNullable(BY_IBAN.get(Iban.normalise(iban)));
    }

    private static Account account(String bankCode, String branch, long number, String holder, String bank) {
        return new Account(Iban.of("GR", bankCode + branch + "%016d".formatted(number)), holder, bank);
    }
}
