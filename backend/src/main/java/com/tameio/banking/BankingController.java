package com.tameio.banking;

import com.tameio.banking.BankingViews.AccountView;
import com.tameio.banking.BankingViews.DemoDeposit;
import com.tameio.banking.BankingViews.OpenAccount;
import com.tameio.banking.BankingViews.TransactionView;
import com.tameio.banking.BankingViews.TransferRequest;
import com.tameio.banking.BankingViews.TransferView;
import com.tameio.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Accounts")
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/accounts")
@PreAuthorize("hasRole('BUSINESS')")
@RequiredArgsConstructor
class BankingController {

    private final BankingService service;

    @Operation(summary = "The business's accounts with their balances")
    @GetMapping
    List<AccountView> accounts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId) {
        return service.accounts(CurrentUser.from(jwt), businessId);
    }

    @Operation(summary = "Open a current account with a new IBAN")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountView open(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId, @Valid @RequestBody OpenAccount request) {
        return service.open(CurrentUser.from(jwt), businessId, request.name());
    }

    @Operation(summary = "The account's statement, newest first, with the balance after each movement")
    @GetMapping("/{accountId}/transactions")
    Page<TransactionView> statement(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId, @PathVariable UUID accountId,
                                    @PageableDefault(size = 25) Pageable pageable) {
        return service.statement(CurrentUser.from(jwt), businessId, accountId, pageable);
    }

    @Operation(summary = "Pay another account. Send an Idempotency-Key so a retry never pays twice")
    @PostMapping("/{accountId}/transfers")
    ResponseEntity<TransferView> transfer(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId, @PathVariable UUID accountId,
                                          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                          @Valid @RequestBody TransferRequest request) {
        BankingService.Transfer transfer = service.transfer(CurrentUser.from(jwt), businessId, accountId, idempotencyKey, request);
        // A replay answers like the original request did, and says so
        return transfer.replayed()
            ? ResponseEntity.ok().header("Idempotent-Replayed", "true").body(transfer.view())
            : ResponseEntity.status(HttpStatus.CREATED).body(transfer.view());
    }

    @Operation(summary = "Local stacks only: receive a demo payment from a customer")
    @PostMapping("/{accountId}/demo-deposits")
    @ResponseStatus(HttpStatus.CREATED)
    TransactionView demoDeposit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId, @PathVariable UUID accountId,
                                @Valid @RequestBody DemoDeposit request) {
        return service.demoDeposit(CurrentUser.from(jwt), businessId, accountId, request.amount());
    }
}
