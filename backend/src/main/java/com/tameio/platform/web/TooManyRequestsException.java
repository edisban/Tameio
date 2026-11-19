package com.tameio.platform.web;

/** Too many attempts in a short time, for example failed sign-ins. Answered with 429. */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
