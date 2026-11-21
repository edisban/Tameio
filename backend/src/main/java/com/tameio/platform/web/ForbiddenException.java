package com.tameio.platform.web;

/**
 * The user is signed in and has the right role, but a business rule forbids this particular action, such
 * as approving a loan above one's approval limit. Answered with 403.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
