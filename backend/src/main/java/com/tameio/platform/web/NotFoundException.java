package com.tameio.platform.web;

/**
 * Nothing found that this user may see. Also used when something exists but belongs to another
 * tenant, so the answer never reveals that it exists. Answered with 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
