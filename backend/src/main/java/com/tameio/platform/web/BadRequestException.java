package com.tameio.platform.web;

/** The request is malformed in a way Bean Validation cannot express. Answered with 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
