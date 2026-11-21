package com.tameio.platform.security;

/**
 * Whether a session token is still honoured. A token is signed and carries its own expiry, so on its
 * own it stays valid for its whole lifetime; this lets the identity module end every session of a user
 * at once (account disabled, password changed or reset, "sign out everywhere"), as banks must.
 */
public interface SessionStatus {

    /** The token claim holding the account's session version at sign-in. */
    String VERSION_CLAIM = "sv";

    /** The token claim set while the user must replace a temporary password. */
    String PASSWORD_CHANGE_CLAIM = "pwc";

    /** True if the account exists, is active and has not ended its sessions since this one began. */
    boolean isCurrent(String userId, long sessionVersion);
}
