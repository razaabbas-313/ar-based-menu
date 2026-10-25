package com.atelier.media.security;

import com.atelier.media.config.AtelierProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Three-tier RBAC on stateless JWTs (roles claim):
 * <ul>
 *   <li>ROLE_SUPER_ADMIN: /api/v1/admin/**</li>
 *   <li>ROLE_RESTAURANT: /api/v1/studio/**, /api/v1/media/**, /api/v1/auth/me, /api/v1/auth/change-password</li>
 *   <li>ROLE_RESTAURANT_SETUP (temporary password): /api/v1/auth/me and /api/v1/auth/change-password only</li>
 *   <li>Guests (anonymous): a single menu by its direct link (/api/v1/public/menu/{slug}) and sign-in</li>
 * </ul>
 * No sessions or cookies, so CSRF protection is not applicable.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AtelierProperties.class)
public class SecurityConfig {

    /** Problem title the frontend recognizes to show the "Set your new password" screen. */
    public static final String PASSWORD_CHANGE_REQUIRED = "Password change required";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, ex) ->
                writeProblem(response, objectMapper, HttpStatus.UNAUTHORIZED, "Sign-in required",
                        "Sign in to your restaurant studio to continue (missing, invalid or expired token).");
        AccessDeniedHandler forbidden = (request, response, ex) -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean setupOnly = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> TokenService.ROLE_RESTAURANT_SETUP.equals(a.getAuthority()));
            if (setupOnly) {
                writeProblem(response, objectMapper, HttpStatus.FORBIDDEN, PASSWORD_CHANGE_REQUIRED,
                        "Set a new password to replace your temporary one before opening the studio.");
            } else {
                writeProblem(response, objectMapper, HttpStatus.FORBIDDEN, "Not allowed",
                        "This account cannot perform that action.");
            }
        };

        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenService.ROLES_CLAIM);
        roles.setAuthorityPrefix("");
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(roles);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; img-src 'self' data: https: blob:; model-src 'self' https: blob:; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; frame-ancestors 'none';"))
                        .frameOptions(f -> f.deny())
                        .contentTypeOptions(Customizer.withDefaults()))
                .addFilterAfter(new org.springframework.web.filter.OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                    jakarta.servlet.http.HttpServletResponse response,
                                                    jakarta.servlet.FilterChain filterChain)
                            throws jakarta.servlet.ServletException, IOException {
                        filterChain.doFilter(request, response);
                        if (request.getRequestURI().startsWith("/api/")) {
                            if (!response.containsHeader("Cache-Control")) {
                                response.setHeader("Cache-Control", "no-store");
                            }
                        }
                    }
                }, org.springframework.security.web.header.HeaderWriterFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Public self-registration is closed (v4): only the super admin provisions restaurants.
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/config").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/admin/login",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password-otp").permitAll()
                        // Guest menus and analytics events
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/menu/*", "/api/v1/restaurants/*/menu").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/analytics/event").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Tier 1: platform owner
                        .requestMatchers("/api/v1/admin/**").hasAuthority(TokenService.ROLE_SUPER_ADMIN)
                        // Tier 2: restaurant owner / manager (and super-admin impersonation)
                        // First sign-in with a temporary password: only "who am I" and "set my password".
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/change-password")
                                .hasAnyAuthority(TokenService.ROLE_RESTAURANT, TokenService.ROLE_RESTAURANT_SETUP)
                        .requestMatchers("/api/v1/media/**", "/api/v1/studio/**").hasAuthority(TokenService.ROLE_RESTAURANT)
                        // Tier 3 (guests) only ever reach the permitAll() routes above.
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden))
                .addFilterAfter(new org.springframework.web.filter.OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                    jakarta.servlet.http.HttpServletResponse response,
                                                    jakarta.servlet.FilterChain filterChain)
                            throws jakarta.servlet.ServletException, IOException {
                        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                        if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                            String path = request.getRequestURI();
                            List<String> aud = jwt.getAudience();

                            if (path.startsWith("/api/v1/admin/")) {
                                if (aud == null || !aud.contains(TokenService.AUDIENCE_ADMIN)) {
                                    writeProblem(response, objectMapper, HttpStatus.FORBIDDEN, "Invalid audience",
                                            "Studio tokens cannot access admin endpoints.");
                                    return;
                                }
                            } else if (path.startsWith("/api/v1/studio/") || path.startsWith("/api/v1/media/")
                                    || path.startsWith("/api/v1/auth/me") || path.startsWith("/api/v1/auth/change-password")) {
                                if (aud == null || !aud.contains(TokenService.AUDIENCE_STUDIO)) {
                                    writeProblem(response, objectMapper, HttpStatus.FORBIDDEN, "Invalid audience",
                                            "Admin tokens cannot access studio endpoints.");
                                    return;
                                }
                            }
                        }
                        filterChain.doFilter(request, response);
                    }
                }, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden));
        return http.build();
    }

    @Bean
    public SecretKey jwtSecretKey(AtelierProperties properties) {
        return JwtKeys.secretKey(properties.jwt().secret());
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return JwtKeys.encoder(jwtSecretKey);
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey, AtelierProperties properties) {
        return JwtKeys.decoder(jwtSecretKey, properties.jwt().issuer());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Bearer tokens are not sent automatically by browsers, so origin "*" is safe for dev; restrict it in production. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AtelierProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        cors.setExposedHeaders(List.of("Cache-Control", "ETag"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    private static void writeProblem(HttpServletResponse response, ObjectMapper mapper,
                                     HttpStatus status, String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
