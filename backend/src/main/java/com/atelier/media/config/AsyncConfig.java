package com.atelier.media.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async (storage wipes) on Spring Boot's auto-configured task executor. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
