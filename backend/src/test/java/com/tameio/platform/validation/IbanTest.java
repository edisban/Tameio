package com.tameio.platform.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IbanTest {

    @Test
    void computesTheCheckDigitsOfAGreekIban() {
        // The example IBAN from the Greek banks' own documentation
        assertThat(Iban.of("GR", "0110125" + "0000000012300695")).isEqualTo("GR1601101250000000012300695");
    }

    @Test
    void printsAnIbanInGroupsOfFour() {
        assertThat(Iban.format("gr1601101250000000012300695")).isEqualTo("GR16 0110 1250 0000 0001 2300 695");
    }

    @Test
    void normalisesWhatPeopleType() {
        assertThat(Iban.normalise(" gr16 0110 1250 ")).isEqualTo("GR1601101250");
    }
}
