package com.tameio.banking;

import com.tameio.platform.persistence.AuditedEntity;
import com.tameio.platform.web.UnprocessableException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A ledger account: a business's current account, or one of the bank's internal accounts. The balance
 * changes only through {@link Ledger}, in the same transaction as the postings that explain it.
 */
@Entity
@Table(name = "bank_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BankAccount extends AuditedEntity {

    enum Kind { CUSTOMER, INTERNAL }

    enum Status { ACTIVE, FROZEN, CLOSED }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Kind kind;

    @Column(name = "business_id", updatable = false)
    private UUID businessId;

    /** For internal accounts: see {@link InternalAccount}. */
    @Column(length = 40, updatable = false)
    private String code;

    @Column(length = 34, updatable = false)
    private String iban;

    @Column(name = "holder_name", length = 200, updatable = false)
    private String holderName;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "status_reason", length = 200)
    private String statusReason;

    static BankAccount openCurrent(UUID businessId, String holderName, String iban, String name) {
        var account = new BankAccount();
        account.kind = Kind.CUSTOMER;
        account.businessId = businessId;
        account.holderName = holderName;
        account.iban = iban;
        account.name = name;
        account.currency = "EUR";
        account.balance = BigDecimal.ZERO.setScale(2);
        account.status = Status.ACTIVE;
        return account;
    }

    boolean isCustomer() {
        return kind == Kind.CUSTOMER;
    }

    /** Moves the balance by a signed amount. A customer account may not go below zero or move while frozen. */
    BigDecimal apply(BigDecimal amount) {
        if (isCustomer() && status != Status.ACTIVE) {
            throw new UnprocessableException(status == Status.FROZEN
                ? "The account " + iban + " is frozen." : "The account " + iban + " is closed.");
        }
        BigDecimal next = balance.add(amount);
        if (isCustomer() && next.signum() < 0) {
            throw new UnprocessableException("The balance is not enough for this payment.");
        }
        balance = next;
        return next;
    }

    void freeze(String reason) {
        status = Status.FROZEN;
        statusReason = reason;
    }

    void unfreeze() {
        status = Status.ACTIVE;
        statusReason = null;
    }
}
