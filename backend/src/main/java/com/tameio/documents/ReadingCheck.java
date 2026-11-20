package com.tameio.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One check document AI ran on the fields it found, such as "net plus VAT equals the total". */
@Embeddable
record ReadingCheck(
    @Column(nullable = false, length = 60) String name,
    @Column(nullable = false) boolean passed,
    @Column(nullable = false, length = 300) String detail) {

    static ReadingCheck of(DocumentAi.Check check) {
        String detail = check.detail() == null ? "" : check.detail();
        return new ReadingCheck(check.name(), check.passed(), detail.length() > 300 ? detail.substring(0, 300) : detail);
    }
}
