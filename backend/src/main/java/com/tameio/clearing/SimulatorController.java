package com.tameio.clearing;

import com.tameio.clearing.ClearingMessages.CreditTransfer;
import com.tameio.platform.validation.Iban;
import com.tameio.platform.validation.ValidIban;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Lets the bank's administrators play the other banks: send a payment in, and see who banks there. */
@Tag(name = "Clearing house simulator")
@RestController
@RequestMapping("/api/v1/clearing")
@RequiredArgsConstructor
class SimulatorController {

    private final RabbitTemplate rabbit;

    record IncomingRequest(
        @NotBlank @ValidIban String creditorIban,
        @NotBlank @Size(max = 140) String debtorName,
        @NotNull @DecimalMin("0.01") @DecimalMax("1000000.00") @Digits(integer = 7, fraction = 2) BigDecimal amount,
        @Size(max = 140) String reference) {
    }

    record Sent(String endToEndId) {
    }

    @Operation(summary = "Another bank sends a SEPA credit transfer to an account at Kyma Bank")
    @PostMapping("/incoming")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('ADMIN')")
    Sent incoming(@Valid @RequestBody IncomingRequest request) {
        String endToEndId = "SIM" + UUID.randomUUID().toString().replace("-", "").substring(0, 32).toUpperCase();
        OtherBanks.Account payer = OtherBanks.ACCOUNTS.getFirst();
        rabbit.convertAndSend(ClearingMessages.BANK_EXCHANGE, "sct.incoming", new CreditTransfer(endToEndId, payer.iban(),
            request.debtorName(), Iban.normalise(request.creditorIban()), null, request.amount(), request.reference()));
        return new Sent(endToEndId);
    }

    @Operation(summary = "Accounts at the simulated other banks, to pay in demos")
    @GetMapping("/directory")
    @PreAuthorize("isAuthenticated()")
    List<OtherBanks.Account> directory() {
        return OtherBanks.ACCOUNTS;
    }
}
