package com.tameio.platform.web;

/** The credentials are wrong. The message never says which part, so it cannot be used to find accounts. Answered with 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
