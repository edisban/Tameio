package com.tameio.identity;

import com.tameio.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@Tag(name = "Businesses")
@RestController
@RequestMapping("/api/v1/businesses")
@PreAuthorize("hasRole('BUSINESS')")
@RequiredArgsConstructor
class BusinessController {

    private final BusinessService service;

    @Operation(summary = "Businesses the signed-in user can act for")
    @GetMapping
    List<BusinessView> mine(@AuthenticationPrincipal Jwt jwt) {
        return service.businessesOf(CurrentUser.from(jwt));
    }

    @Operation(summary = "One of the signed-in user's businesses")
    @GetMapping("/{id}")
    BusinessView one(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.businessOf(CurrentUser.from(jwt), id);
    }

    @Operation(summary = "Register a business; the signed-in user becomes its owner")
    @PostMapping
    ResponseEntity<BusinessView> register(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RegisterBusinessRequest request) {
        BusinessView created = service.register(CurrentUser.from(jwt), request);
        // 201 with a Location header pointing at the new resource
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }
}
