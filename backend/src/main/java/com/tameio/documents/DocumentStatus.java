package com.tameio.documents;

/** Where a document is in its life: stored, then read by document AI. */
public enum DocumentStatus {
    /** Stored and queued for document AI. */
    UPLOADED,
    /** Document AI is reading it; a retry after a failed attempt also shows as reading. */
    READING,
    /** Read: its fields, checks and suggested category are known. */
    READ,
    /** Could not be read; {@link ReadFailure} says why. It can be sent for reading again. */
    FAILED
}
