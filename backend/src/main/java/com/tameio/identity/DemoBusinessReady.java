package com.tameio.identity;

import com.tameio.platform.demo.DemoYear;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A demo business was just created on a local stack (tameio.demo.enabled). Published inside the
 * seeding transaction, so the modules that build its history (accounts, expenses) do it in the same
 * transaction: the demo data is there in full or not at all.
 *
 * <p>Listeners run in a fixed order: the account and its history first, then the expense book, then
 * the loan applications that are assessed on both.
 *
 * @param end    the last day of the demo year; with the seed, every module builds the same year from {@link DemoYear}
 * @param bankers the demo staff, so loan applications can be taken through their real review steps
 */
public record DemoBusinessReady(UUID businessId, String name, DemoYear.Profile profile, LocalDate end, long seed, Bankers bankers) {

    /** A member of the demo bank's staff. */
    public record Banker(String id, String name) {
    }

    public record Bankers(Banker analyst, Banker manager, Banker administrator) {
    }

    /** Order of the listeners, lowest first. */
    public static final int ACCOUNTS = 1;
    public static final int EXPENSES = 2;
    public static final int LOANS = 3;
}
