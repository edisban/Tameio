package com.tameio.platform.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL advisory locks: a lock on a number, held in the database, so every instance of the app sees
 * it. They are transaction-scoped: released at commit or rollback, so an instance that crashes can never
 * leave one behind. Used where several instances would otherwise do the same work at the same time.
 */
@Component
@RequiredArgsConstructor
public class AdvisoryLocks {

    /** Every lock the app uses, with a fixed number each, so two kinds of work never share one by accident. */
    public enum Key {
        DEMO_DATA(1),
        INSTALMENTS_DUE(2);

        private final long id;

        Key(long id) {
            this.id = id;
        }
    }

    private final JdbcTemplate jdbc;

    /** Waits until no other transaction holds the lock, then holds it until this transaction ends. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void acquire(Key key) {
        jdbc.queryForList("select pg_advisory_xact_lock(?)", key.id);
    }

    /** Takes the lock if it is free; false at once when another transaction holds it. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryAcquire(Key key) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, key.id));
    }
}
