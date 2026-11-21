package com.tameio.banking;

import com.tameio.platform.persistence.AuditedEntity;
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
 * A payment and where it stands. The money itself moves in journal entries; this records the payment's
 * life: booked, then, for SEPA, settled by the clearing house or returned with a reason.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Payment extends AuditedEntity {

    enum Direction { OUTGOING, INCOMING }

    enum Scheme { INTERNAL, SEPA }

    enum Status {
        /** Booked at once, within the bank. */
        COMPLETED,
        /** Sent to clearing; the money has left the account but not yet the bank. */
        PENDING,
        /** Cleared and settled between the banks. */
        SETTLED,
        /** Rejected by the other side; the money came back. */
        RETURNED
    }

    @Column(name = "business_id", nullable = false, updatable = false)
    private UUID businessId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private Scheme scheme;

    @Column(name = "end_to_end_id", nullable = false, length = 35, updatable = false)
    private String endToEndId;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "counterparty_iban", nullable = false, length = 34, updatable = false)
    private String counterpartyIban;

    @Column(name = "counterparty_name", nullable = false, length = 140, updatable = false)
    private String counterpartyName;

    @Column(length = 140, updatable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "reason_code", length = 4)
    private String reasonCode;

    @Column(length = 200)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payee_check", length = 20, updatable = false)
    private PayeeCheck.Result payeeCheck;

    @Column(name = "booking_entry_id", updatable = false)
    private UUID bookingEntryId;

    @Column(name = "final_entry_id")
    private UUID finalEntryId;

    static Payment outgoing(BankAccount from, Scheme scheme, String endToEndId, BigDecimal amount, String creditorIban,
                            String creditorName, String reference, PayeeCheck.Result payeeCheck, UUID bookingEntryId) {
        var payment = new Payment();
        payment.businessId = from.getBusinessId();
        payment.accountId = from.getId();
        payment.direction = Direction.OUTGOING;
        payment.scheme = scheme;
        payment.endToEndId = endToEndId;
        payment.amount = amount;
        payment.counterpartyIban = creditorIban;
        payment.counterpartyName = creditorName;
        payment.reference = reference;
        payment.payeeCheck = payeeCheck;
        payment.bookingEntryId = bookingEntryId;
        payment.status = scheme == Scheme.INTERNAL ? Status.COMPLETED : Status.PENDING;
        return payment;
    }

    static Payment incoming(BankAccount to, String endToEndId, BigDecimal amount, String debtorIban, String debtorName,
                            String reference, UUID bookingEntryId) {
        var payment = new Payment();
        payment.businessId = to.getBusinessId();
        payment.accountId = to.getId();
        payment.direction = Direction.INCOMING;
        payment.scheme = Scheme.SEPA;
        payment.endToEndId = endToEndId;
        payment.amount = amount;
        payment.counterpartyIban = debtorIban;
        payment.counterpartyName = debtorName;
        payment.reference = reference;
        payment.bookingEntryId = bookingEntryId;
        payment.status = Status.SETTLED;
        return payment;
    }

    boolean isPending() {
        return status == Status.PENDING;
    }

    void settled(UUID entryId) {
        status = Status.SETTLED;
        finalEntryId = entryId;
    }

    void returned(UUID entryId, String reasonCode, String reason) {
        status = Status.RETURNED;
        finalEntryId = entryId;
        this.reasonCode = reasonCode;
        this.reason = reason;
    }
}
