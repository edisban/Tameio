package com.tameio.banking;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PayeeNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
        ΧΑΡΤΙΚΑ ΑΙΓΑΙΟΥ Ο.Ε.         | Χαρτικά Αιγαίου ΟΕ           | MATCH
        Νικολάου Γεώργιος            | ΓΕΩΡΓΙΟΣ ΝΙΚΟΛΑΟΥ            | MATCH
        Aegean Papers S.A.           | AEGEAN PAPERS                | MATCH
        Χαρτικα Αιγαιυο              | ΧΑΡΤΙΚΑ ΑΙΓΑΙΟΥ Ο.Ε.         | CLOSE_MATCH
        Βήτα                         | Βήτα Αρτοποιία               | CLOSE_MATCH
        Άλφα Καφέ                    | Βήτα Αρτοποιία               | NO_MATCH
        Α.Ε.                         | Βήτα Αρτοποιία Α.Ε.          | NO_MATCH
        """)
    void comparesNamesTheWayVerificationOfPayeeDoes(String typed, String actual, PayeeCheck.Result expected) {
        assertThat(PayeeNames.compare(typed, actual)).isEqualTo(expected);
    }
}
