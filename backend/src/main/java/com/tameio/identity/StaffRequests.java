package com.tameio.identity;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What the staff administration API takes and returns. */
final class StaffRequests {

    private StaffRequests() {
    }

    /** @param approvalLimit the largest loan this person may approve; empty for none */
    record CreateStaff(
        @NotBlank(message = "Email is required") @Email(message = "Not a valid email address") @Size(max = 254) String email,
        @NotBlank(message = "Name is required") @Size(max = 200, message = "Name must be at most 200 characters") String fullName,
        @NotNull(message = "Role is required") UserRole role,
        @DecimalMin(value = "0.00", message = "The limit cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "At most two decimals") BigDecimal approvalLimit) {

        CreateStaff {
            email = email == null ? null : email.strip();
            fullName = fullName == null ? null : fullName.strip();
        }
    }

    record UpdateStaff(
        @NotNull(message = "Role is required") UserRole role,
        @DecimalMin(value = "0.00", message = "The limit cannot be negative")
        @Digits(integer = 12, fraction = 2, message = "At most two decimals") BigDecimal approvalLimit,
        @NotNull(message = "Say whether the account is active") Boolean active) {
    }

    record StaffView(UUID id, String email, String fullName, UserRole role, BigDecimal approvalLimit, boolean active,
                     boolean mustChangePassword, boolean locked, Instant createdAt) {

        static StaffView of(UserAccount a, Instant now) {
            return new StaffView(a.getId(), a.getEmail(), a.getFullName(), a.getRole(), a.getApprovalLimit(), a.isActive(),
                a.isMustChangePassword(), a.isLocked(now), a.getCreatedAt());
        }
    }

    /** Shown once, to the administrator who created or reset the account; never stored in plain text. */
    record WithTemporaryPassword(StaffView user, String temporaryPassword) {
    }
}
