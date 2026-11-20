package com.tameio.documents;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Document AI for tests. What it answers depends on a word in the uploaded file, so each test picks the
 * behaviour it needs and tests running at the same time never disturb each other:
 * <ul>
 *   <li>{@code unreadable}: always answers that the file cannot be read;</li>
 *   <li>{@code unreadable-once}: cannot be read the first time, then reads normally;</li>
 *   <li>{@code down}: never answers;</li>
 *   <li>{@code flaky}: does not answer the first time, then reads normally;</li>
 *   <li>anything else: reads the {@link #RECEIPT} receipt.</li>
 * </ul>
 * Tests add it with {@code @Import}; it then replaces the HTTP client.
 */
@TestComponent
@Primary
class ScriptedDocumentAi implements DocumentAi {

    static final BigDecimal TOTAL = new BigDecimal("12.40");
    static final String ISSUER_AFM = "094014201";
    static final LocalDate ISSUED = LocalDate.of(2026, 11, 14);

    static final Result RECEIPT = new Result(
        "ΠΡΑΤΗΡΙΟ ΑΙΓΑΙΟΥ\nΑΦΜ 094014201\n14/11/2026\nΣΥΝΟΛΟ 12,40", 1, "text-layer",
        new Fields(new TextField(ISSUER_AFM, 0.98), new DateField(ISSUED, 0.95), new TextField("10.00", 0.9),
            new TextField("2.40", 0.9), new TextField("12.40", 0.97)),
        List.of(new Check("afm_check_digit", true, "The ΑΦΜ check digit is valid."),
            new Check("totals_add_up", true, "10.00 + 2.40 = 12.40")),
        new Category(new Suggestion("FUEL", 0.91), List.of(new Suggestion("TRANSPORT", 0.05)), "expense-classifier@test"),
        42);

    private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    @Override
    public Result read(byte[] content, String mediaType) {
        String text = new String(content, StandardCharsets.UTF_8);
        int call = calls.computeIfAbsent(text, key -> new AtomicInteger()).incrementAndGet();
        if (text.contains("unreadable-once")) {
            if (call == 1) {
                throw new Unreadable(ReadFailure.UNREADABLE, "No text found in the document.");
            }
            return RECEIPT;
        }
        if (text.contains("unreadable")) {
            throw new Unreadable(ReadFailure.UNREADABLE, "No text found in the document.");
        }
        if (text.contains("down") || (text.contains("flaky") && call == 1)) {
            throw new Unavailable("Document AI could not be reached", null);
        }
        return RECEIPT;
    }

    /** How many times this exact file was sent for reading. */
    int callsFor(byte[] content) {
        AtomicInteger count = calls.get(new String(content, StandardCharsets.UTF_8));
        return count == null ? 0 : count.get();
    }
}
