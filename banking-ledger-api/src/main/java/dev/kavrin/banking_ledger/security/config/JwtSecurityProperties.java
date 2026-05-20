package dev.kavrin.banking_ledger.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "banking-ledger.security.jwt")
public record JwtSecurityProperties(
        @NotBlank
        String issuer,
        @NotBlank
        String audience,
        @NotBlank
        @Size(min = 32)
        String secret,
        @NotNull
        Duration clockSkew,
        @NotNull
        Duration accessTokenTtl,
        @NotEmpty
        List<String> allowedCorsOrigins
) {
}
