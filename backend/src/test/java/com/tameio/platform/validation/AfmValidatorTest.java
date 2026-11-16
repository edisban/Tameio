package com.tameio.platform.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AfmValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"090000045", "094014201", "998123451", "800012340"})
    void acceptsNumbersWithTheRightCheckDigit(String afm) {
        assertThat(AfmValidator.isValid(afm)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "123456789",  // wrong check digit
        "090000046",  // a valid number with its last digit changed
        "000000000",  // all zeros has a "correct" check digit but is never issued
        "09000004",   // too short
        "0900000450", // too long
        "09000004A",  // not all digits
        ""})
    void rejectsEverythingElse(String afm) {
        assertThat(AfmValidator.isValid(afm)).isFalse();
    }
}
