package com.tameio.identity;

import com.tameio.platform.demo.DemoYear;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A demo business was just created on a local stack (tameio.demo.enabled). Published inside the
 * seeding transaction, so the modules that build its history (accounts, expenses) do it in the same
 * transaction: the demo data is there in full or not at all.
 *
 * @param end  the last day of the demo year; with the seed, every module builds the same year from {@link DemoYear}
 */
public record DemoBusinessReady(UUID businessId, String name, DemoYear.Profile profile, LocalDate end, long seed) {
}
