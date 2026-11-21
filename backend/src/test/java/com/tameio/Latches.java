package com.tameio;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** For tests that play two instances at once on two threads. */
public final class Latches {

    public static final long WAIT_SECONDS = 5;

    private Latches() {
    }

    /** Waits for the other thread, failing the test instead of hanging when it never gets there. */
    public static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
