package com.tameio.platform.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs. Each one guards itself with an {@link AdvisoryLocks} key, as every instance runs it. */
@Configuration
@EnableScheduling
class SchedulingConfig {
}
