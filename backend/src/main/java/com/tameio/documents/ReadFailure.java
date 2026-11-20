package com.tameio.documents;

/** Why a document could not be read. Clients show their own message for each. */
public enum ReadFailure {
    /** Document AI found no text in it: a blank page, a blurry photo. Retrying will not help. */
    UNREADABLE,
    /** Document AI refused the file itself, for example as too large. Retrying will not help. */
    REJECTED,
    /** Document AI did not answer after every retry. Reading it again later may work. */
    GAVE_UP
}
