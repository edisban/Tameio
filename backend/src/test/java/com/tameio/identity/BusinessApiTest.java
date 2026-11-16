package com.tameio.identity;

import com.jayway.jsonpath.JsonPath;
import com.tameio.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * The businesses API end to end: real HTTP handling, real security rules and a real PostgreSQL in a
 * container. Tokens are built in the test (no Keycloak needed) with the claims Keycloak would send.
 * Every test uses its own users and ΑΦΜs, so tests never depend on each other or on order.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BusinessApiTest {

    @Autowired
    MockMvcTester mvc;

    private static RequestPostProcessor user(String... roles) {
        String id = UUID.randomUUID().toString();
        return jwt()
            .jwt(token -> token.subject(id).claim("name", "Maria Test").claim("email", "maria@example.com"))
            .authorities(Arrays.stream(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private static RequestPostProcessor businessUser() {
        return user("BUSINESS");
    }

    /** A valid ΑΦΜ no other test uses: eight random digits and the matching check digit. */
    private static String freshAfm() {
        String first = String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (first.charAt(i) - '0') << (8 - i);
        }
        return first + sum % 11 % 10;
    }

    private MvcTestResult register(RequestPostProcessor user, String afm, String name) {
        return mvc.post().uri("/api/v1/businesses").with(user)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"afm": "%s", "name": "%s"}""".formatted(afm, name))
            .exchange();
    }

    @Test
    void registersABusinessAndMakesTheUserItsOwner() throws Exception {
        RequestPostProcessor maria = businessUser();
        String afm = freshAfm();

        MvcTestResult created = register(maria, afm, "  Καφέ Το Στέκι ");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        assertThat(created).headers().hasValue("Location", "http://localhost/api/v1/businesses/" + id);
        assertThat(created).bodyJson().extractingPath("$.name").isEqualTo("Καφέ Το Στέκι");
        assertThat(created).bodyJson().extractingPath("$.role").isEqualTo("OWNER");

        assertThat(mvc.get().uri("/api/v1/businesses").with(maria))
            .hasStatusOk()
            .bodyJson().extractingPath("$[*].afm").asArray().containsExactly(afm);
        assertThat(mvc.get().uri("/api/v1/businesses/{id}", id).with(maria))
            .hasStatusOk()
            .bodyJson().extractingPath("$.afm").isEqualTo(afm);
    }

    @Test
    void acceptsAnAfmTypedWithSpacesAndStoresTheDigits() {
        String afm = freshAfm();

        assertThat(register(businessUser(), afm.substring(0, 3) + " " + afm.substring(3, 6) + " " + afm.substring(6), "Συνεργείο"))
            .hasStatus(HttpStatus.CREATED)
            .bodyJson().extractingPath("$.afm").isEqualTo(afm);
    }

    @Test
    void rejectsAnInvalidAfmWithAProblemDetail() {
        assertThat(register(businessUser(), "123456789", "Λάθος ΑΦΜ"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
            .bodyJson().extractingPath("$.errors.afm").isEqualTo("Not a valid ΑΦΜ");
    }

    @Test
    void rejectsASecondBusinessWithTheSameAfm() {
        String afm = freshAfm();
        assertThat(register(businessUser(), afm, "Πρώτη")).hasStatus(HttpStatus.CREATED);

        assertThat(register(businessUser(), afm, "Δεύτερη"))
            .hasStatus(HttpStatus.CONFLICT)
            .bodyJson().extractingPath("$.detail").isEqualTo("A business with this ΑΦΜ is already registered.");
    }

    @Test
    void someoneElsesBusinessIsInvisible() throws Exception {
        MvcTestResult created = register(businessUser(), freshAfm(), "Ιατρείο");
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        RequestPostProcessor stranger = businessUser();

        assertThat(mvc.get().uri("/api/v1/businesses").with(stranger))
            .hasStatusOk()
            .bodyJson().isEqualTo("[]");
        // Not found rather than forbidden, so the answer doesn't reveal that the business exists
        assertThat(mvc.get().uri("/api/v1/businesses/{id}", id).with(stranger))
            .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void requiresSignIn() {
        assertThat(mvc.get().uri("/api/v1/businesses")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requiresTheBusinessRole() {
        assertThat(mvc.get().uri("/api/v1/businesses").with(user("ANALYST"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void meDescribesTheSignedInUser() {
        assertThat(mvc.get().uri("/api/v1/me").with(businessUser()))
            .hasStatusOk()
            .bodyJson().satisfies(json -> {
                assertThat(json).extractingPath("$.name").isEqualTo("Maria Test");
                assertThat(json).extractingPath("$.roles").asArray().containsExactly("BUSINESS");
            });
    }
}
