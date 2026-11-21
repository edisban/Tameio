package com.tameio.identity;

import com.tameio.TestcontainersConfiguration;
import com.tameio.platform.security.SessionCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Sign-up, sign-in and the session cookie end to end, with real password hashing and token signing.
 * Each test uses a new email, so tests never share accounts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthApiTest {

    private static final String PASSWORD = "correct horse battery";

    private final MockMvcTester mvc;
    private final UserAccountRepository accounts;

    AuthApiTest(MockMvcTester mvc, UserAccountRepository accounts) {
        this.mvc = mvc;
        this.accounts = accounts;
    }

    @Test
    void storesPasswordsAsArgon2idHashes() {
        String email = freshEmail();
        signUp(email, PASSWORD);

        String hash = accounts.findByEmail(email).orElseThrow().getPasswordHash();

        assertThat(hash).startsWith("{argon2}$argon2id$").doesNotContain(PASSWORD);
    }

    @Test
    void upgradesAnOlderBcryptHashOnSignIn() {
        String email = freshEmail();
        String bcrypt = "{bcrypt}" + new BCryptPasswordEncoder().encode(PASSWORD);
        accounts.save(UserAccount.create(email, bcrypt, "Old Account", UserRole.BUSINESS));

        assertThat(signIn(email, PASSWORD)).hasStatusOk();

        assertThat(accounts.findByEmail(email).orElseThrow().getPasswordHash()).startsWith("{argon2}$argon2id$");
        // The new hash keeps working
        assertThat(signIn(email, PASSWORD)).hasStatusOk();
    }

    private static String freshEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private MvcTestResult signUp(String email, String password) {
        return mvc.post().uri("/api/v1/auth/sign-up").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName": "Emma Wilson", "email": "%s", "password": "%s"}""".formatted(email, password))
            .exchange();
    }

    private MvcTestResult signIn(String email, String password) {
        return mvc.post().uri("/api/v1/auth/sign-in").with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email": "%s", "password": "%s"}""".formatted(email, password))
            .exchange();
    }

    private static Cookie session(MvcTestResult result) {
        return result.getResponse().getCookie(SessionCookies.SESSION);
    }

    @Test
    void signsUpAsABusinessAndStartsASession() throws Exception {
        MvcTestResult result = signUp(freshEmail(), PASSWORD);

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.roles").asArray().containsExactly("BUSINESS");
        Cookie cookie = session(result);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        // The token is only in the cookie, never in the body where scripts could read it
        assertThat(result.getResponse().getContentAsString()).doesNotContain(cookie.getValue());
    }

    @Test
    void theSessionCookieSignsTheUserIn() {
        String email = freshEmail();
        Cookie cookie = session(signUp(email, PASSWORD));

        assertThat(mvc.get().uri("/api/v1/me").cookie(cookie))
            .hasStatusOk()
            .bodyJson().extractingPath("$.email").isEqualTo(email);
        assertThat(mvc.get().uri("/api/v1/businesses").cookie(cookie)).hasStatusOk();
    }

    @Test
    void signsInWithTheEmailInAnyCase() {
        String email = freshEmail();
        signUp(email, PASSWORD);

        assertThat(signIn(email.toUpperCase(), PASSWORD)).hasStatusOk();
    }

    @Test
    void givesTheSameAnswerForAWrongPasswordAndAnUnknownEmail() throws Exception {
        String email = freshEmail();
        signUp(email, PASSWORD);

        MvcTestResult wrongPassword = signIn(email, "wrong password");
        MvcTestResult unknownEmail = signIn(freshEmail(), PASSWORD);

        assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getResponse().getContentAsString()).isEqualTo(unknownEmail.getResponse().getContentAsString());
        assertThat(session(wrongPassword)).isNull();
    }

    @Test
    void locksTheAccountAfterFiveWrongPasswords() {
        String email = freshEmail();
        signUp(email, PASSWORD);

        for (int i = 0; i < UserAccount.MAX_FAILED_SIGN_INS; i++) {
            assertThat(signIn(email, "wrong password")).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        // Even the right password is refused while the account is locked
        assertThat(signIn(email, PASSWORD)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void refusesASecondAccountWithTheSameEmail() {
        String email = freshEmail();
        signUp(email, PASSWORD);

        assertThat(signUp(email, PASSWORD)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void refusesAShortPassword() {
        assertThat(signUp(freshEmail(), "short"))
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson().extractingPath("$.errors.password").isEqualTo("Password must be 8 to 128 characters");
    }

    @Test
    void signOutClearsTheCookie() {
        MvcTestResult result = mvc.post().uri("/api/v1/auth/sign-out").with(csrf()).exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(session(result).getMaxAge()).isZero();
    }

    @Test
    void refusesAStateChangingRequestWithoutTheCsrfToken() {
        Cookie cookie = session(signUp(freshEmail(), PASSWORD));

        // Another site could make the browser send the cookie, but it cannot read the CSRF token
        MvcTestResult result = mvc.post().uri("/api/v1/businesses").cookie(cookie)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"afm": "094014201", "name": "Επίθεση"}""")
            .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void refusesAForgedSessionCookie() {
        Cookie forged = new Cookie(SessionCookies.SESSION, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiJ9.not-a-real-signature");

        assertThat(mvc.get().uri("/api/v1/me").cookie(forged)).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
