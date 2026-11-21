package com.tameio.identity;

import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.security.SessionCookies;
import com.tameio.platform.security.SessionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Me")
@RestController
@RequiredArgsConstructor
class MeController {

    private final AuthService auth;
    private final SessionCookies cookies;

    @Operation(summary = "Who the signed-in user is, as the API sees them")
    @GetMapping("/api/v1/me")
    MeResponse me(@AuthenticationPrincipal Jwt jwt, Authentication authentication) {
        CurrentUser user = CurrentUser.from(jwt);
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length()))
            .sorted()
            .toList();
        return new MeResponse(user.id(), user.name(), user.email(), roles,
            Boolean.TRUE.equals(jwt.getClaim(SessionStatus.PASSWORD_CHANGE_CLAIM)));
    }

    @Operation(summary = "Replace the password; ends every other session and returns a new one")
    @PostMapping("/api/v1/me/password")
    ResponseEntity<MeResponse> changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AuthRequests.ChangePassword request) {
        AuthService.SignedIn signedIn = auth.changePassword(jwt.getSubject(), request);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, cookies.issue(signedIn.token()).toString())
            .body(signedIn.me());
    }

    @Operation(summary = "End every session of this user on every device, this one included")
    @PostMapping("/api/v1/me/sign-out-everywhere")
    ResponseEntity<Void> signOutEverywhere(@AuthenticationPrincipal Jwt jwt) {
        auth.signOutEverywhere(jwt.getSubject());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }
}
