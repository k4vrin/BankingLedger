package dev.kavrin.banking_ledger.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.kavrin.banking_ledger.security.auth.BankingJwtAuthenticationConverter;
import dev.kavrin.banking_ledger.security.auth.SecurityErrorHandlers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtSecurityProperties properties;
    private final BankingJwtAuthenticationConverter jwtAuthenticationConverter;
    private final SecurityErrorHandlers securityErrorHandlers;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(securityErrorHandlers)
                        .accessDeniedHandler(securityErrorHandlers)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api/v1/dev/auth/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/ops/**").hasAnyRole("OPS_ADMIN", "AUDITOR", "SERVICE")
                        .requestMatchers("/api/v1/transfers/**").hasAnyRole("CUSTOMER", "TELLER", "OPS_ADMIN", "SERVICE")
                        .requestMatchers("/api/v1/accounts/**").hasAnyRole("CUSTOMER", "TELLER", "OPS_ADMIN", "AUDITOR", "SERVICE")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(securityErrorHandlers)
                        .accessDeniedHandler(securityErrorHandlers)
                )
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder() {
        var decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                "aud",
                audiences -> audiences != null && audiences.contains(properties.audience())
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(properties.clockSkew()),
                new JwtIssuerValidator(properties.issuer()),
                audienceValidator
        ));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(
                new ImmutableSecret<>(jwtSecretKey())
        );
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedCorsOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("Location", "X-Correlation-Id"));
        configuration.setMaxAge(3600L);
        configuration.setAllowCredentials(true);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private SecretKeySpec jwtSecretKey() {
        var secret = Objects.requireNonNull(properties.secret(), "JWT secret is required");
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
