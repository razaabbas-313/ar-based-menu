package com.atelier.media.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs (stale upload reaper). Disable with {@code atelier.scheduling.enabled=false}. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "atelier.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
