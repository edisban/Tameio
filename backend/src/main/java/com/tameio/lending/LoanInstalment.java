package com.tameio.lending;

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
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One monthly instalment of a disbursed loan. */
@Getter
@Entity
@Table(name = "loan_instalment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class LoanInstalment extends AuditedEntity {

    enum Status {
        /** Its date has not come yet. */
        SCHEDULED,
        /** Its date has come: the business owes it now. */
        DUE
    }

    @Column(name = "loan_application_id", nullable = false, updatable = false)
    private UUID loanApplicationId;

    @Column(nullable = false, updatable = false)
    private int number;

    @Column(name = "due_on", nullable = false, updatable = false)
    private LocalDate dueOn;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "became_due_at")
    private Instant becameDueAt;

    static LoanInstalment scheduled(UUID loanApplicationId, int number, LocalDate dueOn, BigDecimal amount) {
        LoanInstalment instalment = new LoanInstalment();
        instalment.loanApplicationId = loanApplicationId;
        instalment.number = number;
        instalment.dueOn = dueOn;
        instalment.amount = amount;
        instalment.status = Status.SCHEDULED;
        return instalment;
    }
}
