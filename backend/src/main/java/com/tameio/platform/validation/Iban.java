package com.tameio.platform.validation;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Map;

/**
 * International Bank Account Numbers (ISO 13616). An IBAN is a country code, two check digits and the
 * national account number; moving the first four characters to the end, turning letters into numbers
 * (A = 10 … Z = 35) and taking the result modulo 97 must give 1. That catches every single wrong
 * character and almost every swap of two.
 */
public final class Iban {

    /** Lengths for SEPA countries the app is likely to meet; others are checked by the mod-97 rule only. */
    private static final Map<String, Integer> LENGTHS = Map.ofEntries(
        Map.entry("GR", 27), Map.entry("CY", 28), Map.entry("DE", 22), Map.entry("FR", 27), Map.entry("IT", 27),
        Map.entry("ES", 24), Map.entry("NL", 18), Map.entry("BE", 16), Map.entry("AT", 20), Map.entry("PT", 25),
        Map.entry("IE", 22), Map.entry("LU", 20), Map.entry("BG", 22), Map.entry("RO", 24), Map.entry("GB", 22));

    private static final BigInteger NINETY_SEVEN = BigInteger.valueOf(97);

    private Iban() {
    }

    /** Upper case, without the spaces people type or print it with. */
    public static String normalise(String iban) {
        return iban == null ? null : iban.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    public static boolean isValid(String input) {
        String iban = normalise(input);
        if (iban == null || iban.length() < 15 || iban.length() > 34 || !iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]+")) {
            return false;
        }
        Integer length = LENGTHS.get(iban.substring(0, 2));
        if (length != null && iban.length() != length) {
            return false;
        }
        return mod97(iban.substring(4) + iban.substring(0, 4)) == 1;
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
