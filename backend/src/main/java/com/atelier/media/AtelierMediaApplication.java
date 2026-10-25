package com.atelier.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Caching and scheduling are enabled in {@code CacheConfig} / {@code SchedulingConfig} rather than
 * here, so test slices that load this class don't inherit them.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AtelierMediaApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtelierMediaApplication.class, args);
    }
}
