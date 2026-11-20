package com.tameio.documents;

import java.time.LocalDate;
import java.util.List;

/**
 * The document AI service: reads a file and returns its text, the fields found in it, checks on those
 * fields and a suggested expense category. An interface so tests can stand in for the real service.
 */
interface DocumentAi {

    /**
     * @throws Unreadable  the service answered that this file cannot be read; retrying will not help
     * @throws Unavailable the service did not answer or failed; retrying later may help
     */
    Result read(byte[] content, String mediaType);

    record Result(String text, int pages, String method, Fields fields, List<Check> checks, Category category, long durationMs) {
    }

    record Fields(TextField issuerAfm, DateField issueDate, TextField netAmount, TextField vatAmount, TextField totalAmount) {
    }

    record TextField(String value, double confidence) {
    }

    record DateField(LocalDate value, double confidence) {
    }

    record Check(String name, boolean passed, String detail) {
    }

    record Category(Suggestion best, List<Suggestion> alternatives, String modelVersion) {
    }

    record Suggestion(String category, double confidence) {
    }

    /** A definite answer that this file cannot be read. */
    final class Unreadable extends RuntimeException {

        private final ReadFailure failure;

        Unreadable(ReadFailure failure, String detail) {
            super(detail);
            this.failure = failure;
        }

        ReadFailure failure() {
            return failure;
        }
    }

    /** No usable answer: a timeout, a refused connection, a server error. */
    final class Unavailable extends RuntimeException {

        Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
