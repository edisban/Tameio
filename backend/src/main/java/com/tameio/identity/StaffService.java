package com.tameio.identity;

import com.tameio.identity.StaffRequests.CreateStaff;
import com.tameio.identity.StaffRequests.StaffView;
import com.tameio.identity.StaffRequests.UpdateStaff;
import com.tameio.identity.StaffRequests.WithTemporaryPassword;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.web.BadRequestException;
import com.tameio.platform.web.ConflictException;
import com.tameio.platform.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Bank staff accounts, managed by administrators. Staff never sign up: an administrator creates the
 * account with a temporary password, which the person must replace at first sign-in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StaffService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder passwords;
    private final Clock clock;

    Page<StaffView> staff(UserRole role, Pageable pageable) {
        return accounts.findStaff(role, pageable).map(a -> StaffView.of(a, clock.instant()));
    }

    @Transactional
    WithTemporaryPassword create(CurrentUser admin, CreateStaff request) {
        if (request.role() == UserRole.BUSINESS) {
            throw new BadRequestException("Staff have a bank role: ANALYST, MANAGER or ADMIN.");
        }
        String email = UserAccount.normaliseEmail(request.email());
        if (accounts.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }
        String temporary = TemporaryPasswords.generate();
        UserAccount account = accounts.save(UserAccount.staff(email, passwords.encode(temporary), request.fullName(),
            request.role(), limitFor(request.role(), request.approvalLimit())));
        log.info("Staff account {} ({}) created by {}", account.getId(), account.getRole(), admin.id());
        return new WithTemporaryPassword(StaffView.of(account, clock.instant()), temporary);
    }

    @Transactional
    StaffView update(CurrentUser admin, UUID id, UpdateStaff request) {
        if (request.role() == UserRole.BUSINESS) {
            throw new BadRequestException("Staff have a bank role: ANALYST, MANAGER or ADMIN.");
        }
        UserAccount account = find(id);
        boolean self = account.getId().toString().equals(admin.id());
        // An administrator who demotes or disables themselves could leave the bank with nobody to manage access
        if (self && (!request.active() || request.role() != UserRole.ADMIN)) {
            throw new ConflictException("You cannot disable your own account or remove your own administrator role.");
        }
        account.changeRole(request.role(), limitFor(request.role(), request.approvalLimit()));
        if (request.active() && !account.isActive()) {
            account.activate();
            log.info("Staff account {} activated by {}", id, admin.id());
        } else if (!request.active() && account.isActive()) {
            account.deactivate();
            log.info("Staff account {} disabled by {}", id, admin.id());
        }
        return StaffView.of(account, clock.instant());
    }

    @Transactional
    WithTemporaryPassword resetPassword(CurrentUser admin, UUID id) {
        UserAccount account = find(id);
        String temporary = TemporaryPasswords.generate();
        account.resetPassword(passwords.encode(temporary));
        log.info("Password of staff account {} reset by {}", id, admin.id());
        return new WithTemporaryPassword(StaffView.of(account, clock.instant()), temporary);
    }

    /**
     * The largest loan this member of staff may approve, for the lending module's maker-checker rule.
     * Empty if they have no approval authority or are not active staff.
     */
    public Optional<BigDecimal> approvalLimit(String userId) {
        return accounts.findById(UUID.fromString(userId))
            .filter(a -> a.isActive() && a.isStaff())
            .map(UserAccount::getApprovalLimit);
    }

    /** Only managers and administrators can approve; an analyst prepares, never decides. */
    private static BigDecimal limitFor(UserRole role, BigDecimal requested) {
        return role == UserRole.ANALYST ? null : requested;
    }

    private UserAccount find(UUID id) {
        return accounts.findById(id).filter(UserAccount::isStaff)
            .orElseThrow(() -> new NotFoundException("Staff account not found."));
    }
}
