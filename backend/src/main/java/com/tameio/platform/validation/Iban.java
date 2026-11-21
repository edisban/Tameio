package com.tameio.platform.validation;

import java.math.BigInteger;
import java.util.Locale;

/**
 * International Bank Account Numbers (ISO 13616). An IBAN is a country code, two check digits and the
 * national account number. The check digits are chosen so that moving the first four characters to the
 * end, turning letters into numbers (A = 10 … Z = 35) and taking the result modulo 97 gives 1, which
 * catches every single wrong character and almost every swap of two.
 */
public final class Iban {

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    /** Upper case, without the spaces people type or print it with. */
    public static String normalise(String iban) {
        return iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /** Builds an IBAN from a country and national account number, computing its check digits. */
    public static String of(String country, String bban) {
        int check = 98 - mod97(bban + country + "00");
        return country + (check < 10 ? "0" : "") + check + bban;
    }

    /** In groups of four, as it is printed: GR16 0110 1250 0000 0001 2300 695. */
    public static String format(String iban) {
        return normalise(iban).replaceAll("(.{4})(?!$)", "$1 ");
    }

    private static int mod97(String text) {
        StringBuilder digits = new StringBuilder(text.length() * 2);
        for (char c : text.toCharArray()) {
            digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
        }
        return new BigInteger(digits.toString()).mod(NINETY_SEVEN).intValue();
    }
}
