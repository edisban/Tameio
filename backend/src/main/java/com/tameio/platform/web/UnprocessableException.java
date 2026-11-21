package com.tameio.platform.web;

/**
 * The request is well formed but cannot be carried out as things stand, for example a payment larger
 * than the balance. Answered with 422.
 */
public class UnprocessableException extends RuntimeException {

    public UnprocessableException(String message) {
        super(message);
    }
}
