package com.tameio.banking;

import com.tameio.banking.BankingViews.AccountView;
import com.tameio.banking.BankingViews.Freeze;
import com.tameio.banking.BankingViews.TransactionView;
import com.tameio.banking.BankingViews.TrialBalance;
import com.tameio.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Back office: accounts and ledger")
@RestController
@RequestMapping("/api/v1/bank")
@RequiredArgsConstructor
class BackOfficeController {

    private final BackOfficeService service;

    @Operation(summary = "The trial balance: internal accounts, customer deposits, and their sum (always zero)")
    @GetMapping("/ledger/trial-balance")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    TrialBalance trialBalance() {
        return service.trialBalance();
    }

    @Operation(summary = "Customer accounts, searched by holder or IBAN")
    @GetMapping("/accounts")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER', 'ADMIN')")
    Page<AccountView> accounts(@RequestParam(defaultValue = "") String search,
                               @PageableDefault(size = 25, sort = "holderName", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.customerAccounts(search, pageable);
    }

    @Operation(summary = "A customer account's statement")
    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER', 'ADMIN')")
    Page<TransactionView> statement(@PathVariable UUID accountId, @PageableDefault(size = 25) Pageable pageable) {
        return service.statement(accountId, pageable);
    }

    @Operation(summary = "Freeze an account: no money in or out until it is unfrozen")
    @PostMapping("/accounts/{accountId}/freeze")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    AccountView freeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @Valid @RequestBody Freeze request) {
        return service.freeze(CurrentUser.from(jwt), accountId, request.reason());
    }

    @Operation(summary = "Unfreeze an account")
    @PostMapping("/accounts/{accountId}/unfreeze")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    AccountView unfreeze(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
        return service.unfreeze(CurrentUser.from(jwt), accountId);
    }
}
