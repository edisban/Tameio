package com.tameio.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The bodies of the sign-in and sign-up requests. */
final class AuthRequests {

    private AuthRequests() {
    }

    record SignIn(
        @NotBlank(message = "Email is required") String email,
        // Argon2id is costly by design, so an oversized password is refused before it is hashed
        @NotBlank(message = "Password is required") @Size(max = 128, message = "Password must be at most 128 characters") String password) {
    }

    /**
     * A new password: at least 12 characters, as asked of accounts that reach financial data.
     */
    record ChangePassword(
        @NotBlank(message = "Current password is required") @Size(max = 128) String currentPassword,
        @NotBlank(message = "New password is required") @Size(min = 12, max = 128, message = "Password must be 12 to 128 characters") String newPassword) {
    }

    /**
     * At least 8 characters (OWASP's minimum when there is no second factor). The upper limit stops
     * someone from sending megabytes for the server to hash.
     */
    record SignUp(
        @NotBlank(message = "Name is required") @Size(max = 200, message = "Name must be at most 200 characters") String fullName,
        @NotBlank(message = "Email is required") @Email(message = "Not a valid email address") @Size(max = 254) String email,
        @NotBlank(message = "Password is required") @Size(min = 8, max = 128, message = "Password must be 8 to 128 characters") String password) {

        SignUp {
            fullName = fullName == null ? null : fullName.strip();
            email = email == null ? null : email.strip();
        }
    }
}
