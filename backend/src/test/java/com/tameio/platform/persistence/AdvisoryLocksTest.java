package com.tameio.platform.persistence;

import com.tameio.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Advisory locks against a real PostgreSQL: one transaction plays this instance, another a second one. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AdvisoryLocksTest {

    private static final long WAIT_SECONDS = 5;

    private final AdvisoryLocks locks;
    private final TransactionTemplate transaction;

    AdvisoryLocksTest(AdvisoryLocks locks, PlatformTransactionManager transactions) {
        this.locks = locks;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Test
    void aLockIsFreeAgainOnceTheTransactionThatHeldItEnds() {
        transaction.executeWithoutResult(status -> assertThat(locks.tryAcquire(AdvisoryLocks.Key.DEMO_DATA)).isTrue());

        transaction.executeWithoutResult(status -> assertThat(locks.tryAcquire(AdvisoryLocks.Key.DEMO_DATA)).isTrue());
    }

    @Test
    void acquiringWaitsUntilTheOtherInstanceCommits() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> otherInstance = CompletableFuture.runAsync(() -> transaction.executeWithoutResult(status -> {
            locks.acquire(AdvisoryLocks.Key.DEMO_DATA);
            held.countDown();
            await(release);
        }));
        await(held);

        CompletableFuture<Void> thisInstance = CompletableFuture.runAsync(
            () -> transaction.executeWithoutResult(status -> locks.acquire(AdvisoryLocks.Key.DEMO_DATA)));

        assertThat(thisInstance).isNotDone();
        release.countDown();
        otherInstance.get(WAIT_SECONDS, TimeUnit.SECONDS);
        thisInstance.get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
