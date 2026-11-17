package com.tameio.platform.web;

/** The request carries content of a kind the API does not accept. Answered with 415. */
public class UnsupportedMediaTypeException extends RuntimeException {

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
