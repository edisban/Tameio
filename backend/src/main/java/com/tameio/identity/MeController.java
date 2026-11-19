package com.tameio.identity;

import com.tameio.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Me")
@RestController
class MeController {

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
        return new MeResponse(user.id(), user.name(), user.email(), roles);
    }
}
