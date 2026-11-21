package com.tameio.identity;

import com.jayway.jsonpath.JsonPath;
import com.tameio.TestcontainersConfiguration;
import com.tameio.platform.security.SessionCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

/**
 * Bank staff administration with real sign-in, real cookies and real token checks: temporary passwords,
 * the forced password change, disabling accounts and ending sessions at once.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StaffApiTest {

    private static final String ADMIN_PASSWORD = "admin-first-password";
    private static final String NEW_PASSWORD = "a long new password 2026";

    private final MockMvcTester mvc;
    private final UserAccountRepository accounts;
    private final PasswordEncoder passwords;

    private Cookie admin;
    private String adminEmail;

    StaffApiTest(MockMvcTester mvc, UserAccountRepository accounts, PasswordEncoder passwords) {
        this.mvc = mvc;
        this.accounts = accounts;
        this.passwords = passwords;
    }

    @BeforeEach
    void anAdministratorWhoHasChangedTheirPassword() throws Exception {
        String email = freshEmail();
        adminEmail = email;
        accounts.save(UserAccount.staff(email, passwords.encode(ADMIN_PASSWORD), "Άννα Διαχειρίστρια", UserRole.ADMIN, null));
        Cookie temporary = session(signIn(email, ADMIN_PASSWORD));
        admin = session(changePassword(temporary, ADMIN_PASSWORD, NEW_PASSWORD));
    }

    private static String freshEmail() {
        return "staff-" + UUID.randomUUID() + "@bank.example";
    }

    private MvcTestResult signIn(String email, String password) {
        return mvc.post().uri("/api/v1/auth/sign-in").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email": "%s", "password": "%s"}""".formatted(email, password))
            .exchange();
    }

    private MvcTestResult changePassword(Cookie session, String current, String next) {
        return mvc.post().uri("/api/v1/me/password").cookie(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"currentPassword": "%s", "newPassword": "%s"}""".formatted(current, next))
            .exchange();
    }

    private static Cookie session(MvcTestResult result) {
        Cookie cookie = result.getResponse().getCookie(SessionCookies.SESSION);
        assertThat(cookie).as("a session cookie").isNotNull();
        return cookie;
    }

    private MvcTestResult createStaff(String email, String role, String limit) {
        return mvc.post().uri("/api/v1/admin/staff").cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email": "%s", "fullName": "Νίκος Αναλυτής", "role": "%s", "approvalLimit": %s}""".formatted(email, role, limit))
            .exchange();
    }

    private MvcTestResult update(String id, String role, String limit, boolean active) {
        return mvc.put().uri("/api/v1/admin/staff/{id}", id).cookie(admin).with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"role": "%s", "approvalLimit": %s, "active": %s}""".formatted(role, limit, active))
            .exchange();
    }

    private MvcTestResult staffList(Cookie session) {
        return mvc.get().uri("/api/v1/admin/staff").cookie(session).exchange();
    }

    @Test
    void aTemporaryPasswordOpensNothingButThePasswordChange() throws Exception {
        String email = freshEmail();
        String temporary = JsonPath.read(createStaff(email, "MANAGER", "50000").getResponse().getContentAsString(), "$.temporaryPassword");
        assertThat(temporary).matches("[A-Za-z2-9]{4}(-[A-Za-z2-9]{4}){3}");

        MvcTestResult signedIn = signIn(email, temporary);
        assertThat(signedIn).bodyJson().extractingPath("$.mustChangePassword").isEqualTo(true);
        Cookie limited = session(signedIn);

        assertThat(mvc.get().uri("/api/v1/me").cookie(limited).exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/v1/businesses").cookie(limited).exchange()).hasStatus(HttpStatus.FORBIDDEN);

        MvcTestResult changed = changePassword(limited, temporary, NEW_PASSWORD);
        assertThat(changed).hasStatusOk();
        assertThat(changed).bodyJson().extractingPath("$.mustChangePassword").isEqualTo(false);
        // The session with the temporary password has ended; the new one works
        assertThat(mvc.get().uri("/api/v1/me").cookie(limited).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/me").cookie(session(changed)).exchange()).hasStatusOk();
    }

    @Test
    void createsStaffAndListsThem() throws Exception {
        MvcTestResult created = createStaff(freshEmail(), "MANAGER", "50000.00");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.user.role").isEqualTo("MANAGER");
        assertThat(created).bodyJson().extractingPath("$.user.approvalLimit").isEqualTo(50000.00);
        assertThat(created).bodyJson().extractingPath("$.user.mustChangePassword").isEqualTo(true);
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.user.id");
        String hash = accounts.findById(UUID.fromString(id)).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{argon2}");

        assertThat(staffList(admin)).bodyJson().extractingPath("$.content[*].id").asArray().contains(id);
    }

    @Test
    void anAnalystHasNoApprovalLimit() throws Exception {
        MvcTestResult created = createStaff(freshEmail(), "ANALYST", "1000000");

        assertThat(created).bodyJson().extractingPath("$.user.approvalLimit").isNull();
    }

    @Test
    void disablingAnAccountEndsItsSessionsAtOnce() throws Exception {
        String email = freshEmail();
        MvcTestResult created = createStaff(email, "ANALYST", "null");
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.user.id");
        String temporary = JsonPath.read(created.getResponse().getContentAsString(), "$.temporaryPassword");
        Cookie analyst = session(changePassword(session(signIn(email, temporary)), temporary, NEW_PASSWORD));
        assertThat(mvc.get().uri("/api/v1/me").cookie(analyst).exchange()).hasStatusOk();

        assertThat(update(id, "ANALYST", "null", false)).hasStatusOk();

        assertThat(mvc.get().uri("/api/v1/me").cookie(analyst).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(signIn(email, NEW_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);

        assertThat(update(id, "ANALYST", "null", true)).hasStatusOk();
        assertThat(signIn(email, NEW_PASSWORD)).hasStatusOk();
    }

    @Test
    void aResetPasswordEndsSessionsAndMustBeChangedAgain() throws Exception {
        String email = freshEmail();
        MvcTestResult created = createStaff(email, "MANAGER", "10000");
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.user.id");
        String temporary = JsonPath.read(created.getResponse().getContentAsString(), "$.temporaryPassword");
        Cookie manager = session(changePassword(session(signIn(email, temporary)), temporary, NEW_PASSWORD));

        MvcTestResult reset = mvc.post().uri("/api/v1/admin/staff/{id}/reset-password", id).cookie(admin).with(csrf()).exchange();

        assertThat(reset).hasStatusOk();
        String second = JsonPath.read(reset.getResponse().getContentAsString(), "$.temporaryPassword");
        assertThat(second).isNotEqualTo(temporary);
        assertThat(mvc.get().uri("/api/v1/me").cookie(manager).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(signIn(email, NEW_PASSWORD)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(signIn(email, second)).bodyJson().extractingPath("$.mustChangePassword").isEqualTo(true);
    }

    @Test
    void changingSomeonesRoleEndsTheirSessionsSoTheOldRoleGoesWithThem() throws Exception {
        String email = freshEmail();
        MvcTestResult created = createStaff(email, "ADMIN", "null");
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.user.id");
        String temporary = JsonPath.read(created.getResponse().getContentAsString(), "$.temporaryPassword");
        Cookie other = session(changePassword(session(signIn(email, temporary)), temporary, NEW_PASSWORD));
        assertThat(staffList(other)).hasStatusOk();

        assertThat(update(id, "MANAGER", "25000", true)).bodyJson().extractingPath("$.approvalLimit").isEqualTo(25000);

        assertThat(staffList(other)).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(staffList(session(signIn(email, NEW_PASSWORD)))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void anAdministratorCannotLockThemselvesOut() throws Exception {
        String me = JsonPath.read(mvc.get().uri("/api/v1/me").cookie(admin).exchange().getResponse().getContentAsString(), "$.id");

        assertThat(update(me, "ADMIN", "null", false)).hasStatus(HttpStatus.CONFLICT);
        assertThat(update(me, "MANAGER", "1000", true)).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void signingOutEverywhereEndsEverySession() throws Exception {
        // A second device
        Cookie second = session(signIn(adminEmail, NEW_PASSWORD));

        assertThat(mvc.post().uri("/api/v1/me/sign-out-everywhere").cookie(second).with(csrf()).exchange())
            .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(mvc.get().uri("/api/v1/me").cookie(second).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/v1/me").cookie(admin).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refusesWeakPasswordsDuplicateEmailsAndNonAdministrators() throws Exception {
        String email = freshEmail();
        createStaff(email, "ANALYST", "null");
        assertThat(createStaff(email, "ANALYST", "null")).hasStatus(HttpStatus.CONFLICT);
        assertThat(createStaff(freshEmail(), "BUSINESS", "null")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(changePassword(admin, NEW_PASSWORD, "short")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(changePassword(admin, "wrong current password", "another long password")).hasStatus(HttpStatus.UNAUTHORIZED);

        Cookie business = session(mvc.post().uri("/api/v1/auth/sign-up").with(csrf()).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"fullName": "Μαρία", "email": "%s", "password": "business-password"}""".formatted(freshEmail()))
            .exchange());
        assertThat(staffList(business)).hasStatus(HttpStatus.FORBIDDEN);
    }
}
