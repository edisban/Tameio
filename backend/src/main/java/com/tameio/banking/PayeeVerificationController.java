package com.tameio.banking;

import com.tameio.banking.BankingViews.VerifyPayee;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts")
@RestController
@PreAuthorize("hasRole('BUSINESS')")
@RequiredArgsConstructor
class PayeeVerificationController {

    private final BankingService service;

    @Operation(summary = "Verification of Payee: does this name match the account holder's?")
    @PostMapping("/api/v1/payee-verification")
    PayeeCheck.Answer verify(@Valid @RequestBody VerifyPayee request) {
        return service.verifyPayee(request.iban(), request.name());
    }
}
