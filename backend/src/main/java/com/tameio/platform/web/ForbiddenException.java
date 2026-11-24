package com.tameio.platform.web;

/**
 * The user is signed in and has the right role, but a business rule forbids this particular action, such
 * as approving a loan above one's approval limit. Answered with 403. The code names the rule, so a client
 * can explain it in its own language without reading the English detail.
 */
public class ForbiddenException extends RuntimeException {

    private final String code;

    public ForbiddenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
