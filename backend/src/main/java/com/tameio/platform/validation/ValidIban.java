package com.tameio.platform.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A well-formed IBAN with correct check digits. Null is left to {@code @NotBlank}. */
@Documented
@Constraint(validatedBy = ValidIban.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidIban {

    String message() default "Not a valid IBAN";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidIban, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Iban.isValid(value);
        }
    }
}
