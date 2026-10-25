package com.atelier.media.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlEnvironmentPostProcessorTest {

    private final DatabaseUrlEnvironmentPostProcessor processor = new DatabaseUrlEnvironmentPostProcessor();

    @Test
    void translatesRenderPostgresUrlCorrectly() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "DATABASE_URL", "postgres://atelier_user:secret_pass%40123@dpg-c12345.oregon-postgres.render.com:5432/atelier_db?sslmode=require"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://dpg-c12345.oregon-postgres.render.com:5432/atelier_db?sslmode=require");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("atelier_user");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("secret_pass@123");
        assertThat(env.getProperty("spring.flyway.url"))
                .isEqualTo("jdbc:postgresql://dpg-c12345.oregon-postgres.render.com:5432/atelier_db?sslmode=require");
    }

    @Test
    void translatesPostgresqlSchemeWithoutPort() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "DATABASE_URL", "postgresql://dbuser:mypass@postgres.host.internal/mydb"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://postgres.host.internal:5432/mydb");
        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("dbuser");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("mypass");
    }

    @Test
    void leavesExistingJdbcUrlUntouched() {
        ConfigurableEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.datasource.url", "jdbc:postgresql://localhost:5432/existing",
                "DATABASE_URL", "postgres://other:pass@remote:5432/other"
        )));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://localhost:5432/existing");
        assertThat(env.getProperty("spring.datasource.username")).isNull();
    }

    @Test
    void ignoresWhenNoDatabaseUrlConfigured() {
        ConfigurableEnvironment env = new StandardEnvironment();

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("spring.datasource.url")).isNull();
    }
}
