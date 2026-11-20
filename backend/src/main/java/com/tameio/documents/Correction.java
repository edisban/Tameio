package com.tameio.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * A field where the person's value differs from what document AI read. Together with the model
 * version, this is the training data for the next model: what it said, and what was right.
 *
 * @param suggested what document AI read, or null if it found nothing
 */
@Embeddable
record Correction(
    @Column(nullable = false, length = 30) String field,
    @Column(length = 100) String suggested,
    @Column(nullable = false, length = 100) String corrected,
    @Column(name = "model_version", length = 100) String modelVersion) {
}
