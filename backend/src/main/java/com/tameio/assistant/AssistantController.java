package com.tameio.assistant;

import com.tameio.identity.BusinessService;
import com.tameio.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Assistant")
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/assistant")
@PreAuthorize("hasRole('BUSINESS')")
@RequiredArgsConstructor
class AssistantController {

    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    record MessageIn(@Pattern(regexp = "user|assistant") String role, @NotBlank @Size(max = 2000) String content) {
    }

    /** The conversation so far, ending with the user's question; kept short, as the browser holds it. */
    record Conversation(@Size(min = 1, max = 20) List<@Valid MessageIn> messages) {
    }

    private final BusinessService businesses;
    private final AssistantClient assistant;

    @Operation(summary = "Ask the assistant about this business's account, expenses, loans or the bank's terms")
    @PostMapping("/messages")
    AssistantClient.Reply ask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId,
                              @Valid @RequestBody Conversation conversation) {
        // Only for a business the user belongs to; the service then sees nothing but this business
        businesses.checkAccess(CurrentUser.from(jwt), businessId);
        var messages = conversation.messages().stream().map(m -> new AssistantClient.Message(m.role(), m.content())).toList();
        return assistant.ask(businessId, messages);
    }

    @ExceptionHandler(AssistantClient.Unavailable.class)
    ProblemDetail unavailable(AssistantClient.Unavailable e) {
        log.warn("Assistant unavailable: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "The assistant cannot answer right now.");
        problem.setTitle("Assistant unavailable");
        problem.setProperty("code", "ASSISTANT_UNAVAILABLE");
        return problem;
    }
}
