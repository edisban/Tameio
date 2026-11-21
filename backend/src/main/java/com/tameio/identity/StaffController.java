package com.tameio.identity;

import com.tameio.identity.StaffRequests.CreateStaff;
import com.tameio.identity.StaffRequests.StaffView;
import com.tameio.identity.StaffRequests.UpdateStaff;
import com.tameio.identity.StaffRequests.WithTemporaryPassword;
import com.tameio.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Bank staff")
@RestController
@RequestMapping("/api/v1/admin/staff")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
class StaffController {

    private final StaffService service;

    @Operation(summary = "Bank staff, optionally one role")
    @GetMapping
    Page<StaffView> staff(@RequestParam(required = false) UserRole role,
                          @PageableDefault(size = 50, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.staff(role, pageable);
    }

    @Operation(summary = "Create a staff account; the temporary password is returned this once")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WithTemporaryPassword create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateStaff request) {
        return service.create(CurrentUser.from(jwt), request);
    }

    @Operation(summary = "Change role, approval limit, or disable the account (which ends its sessions)")
    @PutMapping("/{id}")
    StaffView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody UpdateStaff request) {
        return service.update(CurrentUser.from(jwt), id, request);
    }

    @Operation(summary = "Set a new temporary password; the person's sessions end")
    @PostMapping("/{id}/reset-password")
    WithTemporaryPassword resetPassword(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.resetPassword(CurrentUser.from(jwt), id);
    }
}
