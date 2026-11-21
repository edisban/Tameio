package com.tameio.accounting;

import com.tameio.accounting.AccountingViews.ExpenseView;
import com.tameio.accounting.AccountingViews.YearSummary;
import com.tameio.documents.ExpenseCategory;
import com.tameio.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Accounting")
@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
@PreAuthorize("hasRole('BUSINESS')")
@RequiredArgsConstructor
class AccountingController {

    private final AccountingService service;

    @Operation(summary = "The expense book for a period, optionally one category; by issue date, newest first")
    @GetMapping("/expenses")
    Page<ExpenseView> expenses(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) ExpenseCategory category,
                               @PageableDefault(size = 50, sort = "issueDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.expenses(CurrentUser.from(jwt), businessId, from, to, category, pageable);
    }

    @Operation(summary = "A year of expenses: totals, every month and quarter, and by category, with input VAT")
    @GetMapping("/accounting/summary")
    YearSummary summary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId, @RequestParam int year) {
        return service.summary(CurrentUser.from(jwt), businessId, year);
    }

    @Operation(summary = "The expense book as a CSV file for Excel (semicolons, decimal commas)")
    @GetMapping(value = "/expenses/export", produces = "text/csv")
    ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        byte[] csv = service.exportCsv(CurrentUser.from(jwt), businessId, from, to);
        String name = "exoda-%s-%s.csv".formatted(from, to);
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
            .body(csv);
    }
}
