package com.tameio.platform.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * The ΑΦΜ check digit: multiply the first eight digits by 2^8 down to 2^1, add them up, take the sum
 * modulo 11 and then modulo 10. The result must equal the ninth digit. All zeros is never valid.
 * Null is left to {@code @NotBlank}, so a missing value gets one clear message rather than two.
 */
public class AfmValidator implements ConstraintValidator<ValidAfm, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || isValid(value);
    }

    public static boolean isValid(String afm) {
        if (afm.length() != 9 || !afm.chars().allMatch(Character::isDigit) || afm.equals("000000000")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (afm.charAt(i) - '0') << (8 - i);
        }
        return sum % 11 % 10 == afm.charAt(8) - '0';
    }
}
