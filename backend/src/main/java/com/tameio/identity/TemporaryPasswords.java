package com.tameio.identity;

import java.security.SecureRandom;

/**
 * Temporary passwords the bank hands to a member of staff, to be replaced at first sign-in. Sixteen
 * characters from an alphabet without look-alikes (no 0/O, 1/l/I), about 90 bits of randomness.
 */
final class TemporaryPasswords {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {
    }

    static String generate() {
        StringBuilder password = new StringBuilder(LENGTH + 3);
        for (int i = 0; i < LENGTH; i++) {
            if (i > 0 && i % 4 == 0) {
                // Groups of four are easier to read out and type
                password.append('-');
            }
            password.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return password.toString();
    }
}
