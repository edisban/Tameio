package com.tameio.banking;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Compares the name a payer typed with the name on the payee's account, the way Verification of Payee
 * schemes do: case, accents, punctuation, word order and legal forms do not count; a small typo is a
 * close match; anything else is not a match.
 */
final class PayeeNames {

    /** Legal forms, written without dots: "Α.Ε." becomes "ΑΕ" before this check. */
    private static final Set<String> LEGAL_FORMS = Set.of(
        "ΑΕ", "ΑΒΕΕ", "ΑΕΒΕ", "ΜΑΕ", "ΕΠΕ", "ΙΚΕ", "ΜΙΚΕ", "ΟΕ", "ΕΕ", "ΚΟΙΝΣΕΠ",
        "SA", "AE", "EPE", "IKE", "OE", "EE", "LTD", "LIMITED", "GMBH", "PLC", "INC", "SRL", "BV");

    private static final double CLOSE = 0.85;

    private PayeeNames() {
    }

    static PayeeCheck.Result compare(String typed, String actual) {
        String a = normalise(typed);
        String b = normalise(actual);
        if (a.isEmpty() || b.isEmpty()) {
            return PayeeCheck.Result.NO_MATCH;
        }
        if (a.equals(b) || tokens(a).equals(tokens(b))) {
            return PayeeCheck.Result.MATCH;
        }
        if (similarity(sorted(a), sorted(b)) >= CLOSE || isShortForm(a, b)) {
            return PayeeCheck.Result.CLOSE_MATCH;
        }
        return PayeeCheck.Result.NO_MATCH;
    }

    /** Upper case without accents, dots or other punctuation, legal forms dropped, single spaces. */
    static String normalise(String name) {
        if (name == null) {
            return "";
        }
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String upper = plain.toUpperCase(Locale.ROOT).replace(".", "").replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
        return Arrays.stream(upper.split(" "))
            .filter(token -> !token.isEmpty() && !LEGAL_FORMS.contains(token))
            .collect(Collectors.joining(" "));
    }

    private static Set<String> tokens(String name) {
        return new TreeSet<>(Arrays.asList(name.split(" ")));
    }

    private static String sorted(String name) {
        return String.join(" ", tokens(name));
    }

    /** "Βήτα" for "Βήτα Αρτοποιία": every word typed is in the real name, which has more. */
    private static boolean isShortForm(String typed, String actual) {
        return tokens(actual).containsAll(tokens(typed));
    }

    /** 1 minus the edit distance over the longer length: 1 is identical. */
    static double similarity(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(substitution, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        int longest = Math.max(a.length(), b.length());
        return longest == 0 ? 1 : 1 - (double) previous[b.length()] / longest;
    }
}
