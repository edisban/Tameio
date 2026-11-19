package com.tameio.identity;

import java.util.List;

/** Who the signed-in user is: returned after signing in and by GET /api/v1/me. */
public record MeResponse(String id, String name, String email, List<String> roles) {
}
