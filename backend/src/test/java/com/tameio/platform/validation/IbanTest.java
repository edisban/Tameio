package com.tameio.platform.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IbanTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "GR1601101250000000012300695",      // the example from the Greek banks' own documentation
        "gr16 0110 1250 0000 0001 2300 695", // as people type it
        "DE89370400440532013000",
        "CY17002001280000001200527600",
    })
    void acceptsRealIbans(String iban) {
        assertThat(Iban.isValid(iban)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "GR1601101250000000012300696",  // one digit wrong
        "GR1601101250000000012300659",  // two digits swapped
        "GR160110125000000001230069",   // too short for Greece
        "XX00",
        "",
        "GR16-0110",
    })
    void refusesWrongOnes(String iban) {
        assertThat(Iban.isValid(iban)).isFalse();
    }

    @Test
    void computesCheckDigitsAndFormatsInGroupsOfFour() {
        String iban = Iban.of("GR", "0110125" + "0000000012300695");

        assertThat(iban).isEqualTo("GR1601101250000000012300695");
        assertThat(Iban.format(iban)).isEqualTo("GR16 0110 1250 0000 0001 2300 695");
    }
}
