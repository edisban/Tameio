package com.tameio.platform.web;

/** The request clashes with data that already exists, such as a second business with the same ΑΦΜ. Answered with 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
