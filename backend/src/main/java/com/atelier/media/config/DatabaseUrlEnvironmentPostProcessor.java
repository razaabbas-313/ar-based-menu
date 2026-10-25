package com.atelier.media.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Automatically translates cloud database connection strings (Render, Railway, Heroku, Supabase, Neon)
 * formatted as {@code postgres://...} or {@code postgresql://...} into Spring Boot JDBC properties.
 *
 * <p>Render Managed PostgreSQL sets {@code DATABASE_URL=postgres://user:password@host:port/db}.
 * Standard HikariCP requires a {@code jdbc:postgresql://...} URL, and standard JDBC does not
 * automatically parse embedded credentials from URI authority. This post-processor translates that into:
 * <ul>
 *   <li>{@code spring.datasource.url = jdbc:postgresql://host:port/db}</li>
 *   <li>{@code spring.datasource.username = user}</li>
 *   <li>{@code spring.datasource.password = password}</li>
 *   <li>Corresponding Flyway properties.</li>
 * </ul>
 *
 * <p>If {@code spring.datasource.url} or {@code DB_URL} is already a valid {@code jdbc:...} URL,
 * it is untouched.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String PROPERTY_SOURCE_NAME = "cloudDatabaseUrlPostProcessor";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String existingUrl = environment.getProperty("spring.datasource.url");
        if (existingUrl != null && existingUrl.startsWith("jdbc:")) {
            return;
        }

        String dbUrlProp = environment.getProperty("DB_URL");
        if (dbUrlProp != null && dbUrlProp.startsWith("jdbc:")) {
            return;
        }

        String rawUrl = environment.getProperty("DATABASE_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            rawUrl = System.getenv("DATABASE_URL");
        }
        if (rawUrl == null || rawUrl.isBlank()) {
            if (dbUrlProp != null && (dbUrlProp.startsWith("postgres://") || dbUrlProp.startsWith("postgresql://"))) {
                rawUrl = dbUrlProp;
            }
        }

        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        try {
            String cleanUrl = rawUrl.strip();
            if (!cleanUrl.startsWith("postgres://") && !cleanUrl.startsWith("postgresql://")) {
                return;
            }

            URI uri = URI.create(cleanUrl.replaceFirst("^postgres://", "http://").replaceFirst("^postgresql://", "http://"));
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return;
            }

            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();
            if (path == null || path.isBlank() || path.equals("/")) {
                path = "/atelier_db";
            }
            String query = uri.getQuery();

            StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://").append(host);
            if (port > 0) {
                jdbcUrl.append(":").append(port);
            }
            jdbcUrl.append(path);
            if (query != null && !query.isBlank()) {
                jdbcUrl.append("?").append(query);
            }

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", jdbcUrl.toString());
            props.put("spring.flyway.url", jdbcUrl.toString());

            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] parts = userInfo.split(":", 2);
                String user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                props.put("spring.datasource.username", user);
                props.put("spring.flyway.user", user);
                if (parts.length > 1) {
                    String pass = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                    props.put("spring.datasource.password", pass);
                    props.put("spring.flyway.password", pass);
                }
            }

            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        } catch (Exception ex) {
            System.err.println("DatabaseUrlEnvironmentPostProcessor failed to parse DATABASE_URL: " + ex.getMessage());
        }
    }
}
