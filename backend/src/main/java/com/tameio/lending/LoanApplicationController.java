package com.tameio.lending;

import com.tameio.lending.LoanViews.ApplicationView;
import com.tameio.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Loans")
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/loan-applications")
@PreAuthorize("hasRole('BUSINESS')")
@RequiredArgsConstructor
class LoanApplicationController {

    private final LoanApplicationService service;

    @Operation(summary = "The business's loan applications, newest first")
    @GetMapping
    List<ApplicationView> applications(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId) {
        return service.applications(CurrentUser.from(jwt), businessId);
    }

    @Operation(summary = "One application")
    @GetMapping("/{id}")
    ApplicationView application(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId, @PathVariable UUID id) {
        return service.application(CurrentUser.from(jwt), businessId, id);
    }

    @Operation(summary = "Start a draft; it is filled in from the business's books and account")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ApplicationView draft(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId, @Valid @RequestBody LoanTerms terms) {
        return service.draft(CurrentUser.from(jwt), businessId, terms);
    }

    @Operation(summary = "Change a draft")
    @PutMapping("/{id}")
    ApplicationView change(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId, @PathVariable UUID id,
                           @Valid @RequestBody LoanTerms terms) {
        return service.change(CurrentUser.from(jwt), businessId, id, terms);
    }

    @Operation(summary = "Send a draft to the bank")
    @PostMapping("/{id}/submit")
    ApplicationView submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId, @PathVariable UUID id) {
        return service.submit(CurrentUser.from(jwt), businessId, id);
    }
}
