package com.tameio.identity;

import java.util.List;

/**
 * Who the signed-in user is: returned after signing in and by GET /api/v1/me.
 *
 * @param mustChangePassword the user holds a temporary password; nothing else works until it is replaced
 */
public record MeResponse(String id, String name, String email, List<String> roles, boolean mustChangePassword) {
}
