package com.tameio.identity;

import com.tameio.platform.security.SessionCookies;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign up, sign in and sign out. The session token goes into an HttpOnly cookie, never into the body. */
@Tag(name = "Sign-in")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {

    private final AuthService service;
    private final SessionCookies cookies;

    @Operation(summary = "Create a business account and sign in")
    @PostMapping("/sign-up")
    ResponseEntity<MeResponse> signUp(@Valid @RequestBody AuthRequests.SignUp request) {
        AuthService.SignedIn signedIn = service.signUp(request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .header(HttpHeaders.SET_COOKIE, cookies.issue(signedIn.token()).toString())
            .body(signedIn.me());
    }

    @Operation(summary = "Sign in with email and password")
    @PostMapping("/sign-in")
    ResponseEntity<MeResponse> signIn(@Valid @RequestBody AuthRequests.SignIn request) {
        AuthService.SignedIn signedIn = service.signIn(request);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.issue(signedIn.token()).toString())
            .body(signedIn.me());
    }

    @Operation(summary = "Sign out: removes the session cookie")
    @PostMapping("/sign-out")
    ResponseEntity<Void> signOut() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }
}
