package com.tameio.identity;

import com.tameio.platform.security.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Answers, for every request, whether its session token is still honoured. */
@Component
@RequiredArgsConstructor
class AccountSessions implements SessionStatus {

    private final UserAccountRepository accounts;

    @Override
    public boolean isCurrent(String userId, long sessionVersion) {
        try {
            return accounts.isSessionCurrent(UUID.fromString(userId), sessionVersion);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
