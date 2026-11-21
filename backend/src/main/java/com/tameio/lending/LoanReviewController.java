package com.tameio.lending;

import com.tameio.lending.LoanViews.CaseView;
import com.tameio.lending.LoanViews.QueueItem;
import com.tameio.lending.LoanViews.Verdict;
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

@Tag(name = "Back office: loans")
@RestController
@RequestMapping("/api/v1/bank/loan-applications")
@RequiredArgsConstructor
class LoanReviewController {

    private final LoanReviewService service;

    @Operation(summary = "Applications sent to the bank, optionally one status, newest first")
    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER', 'ADMIN')")
    Page<QueueItem> queue(@RequestParam(required = false) LoanStatus status,
                          @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.queue(status, pageable);
    }

    @Operation(summary = "The case: figures, risk score with its factors, and every review step")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'MANAGER', 'ADMIN')")
    CaseView caseOf(@PathVariable UUID id) {
        return service.caseOf(id);
    }

    @Operation(summary = "Take a submitted application into review")
    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ANALYST')")
    CaseView startReview(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.startReview(CurrentUser.from(jwt), id);
    }

    @Operation(summary = "Recommend approving or declining, with the reasons")
    @PostMapping("/{id}/recommendation")
    @PreAuthorize("hasRole('ANALYST')")
    CaseView recommend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Verdict verdict) {
        return service.recommend(CurrentUser.from(jwt), id, verdict);
    }

    @Operation(summary = "Approve or decline; needs an approval limit covering the amount, and someone other than the recommender")
    @PostMapping("/{id}/decision")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    CaseView decide(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody Verdict verdict) {
        return service.decide(CurrentUser.from(jwt), id, verdict);
    }

    @Operation(summary = "Pay an approved loan into the business's account")
    @PostMapping("/{id}/disbursement")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    CaseView disburse(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.disburse(CurrentUser.from(jwt), id);
    }
}
