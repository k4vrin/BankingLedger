package dev.kavrin.banking_ledger.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "banking-ledger.security.jwt")
public record JwtSecurityProperties(
        String issuer,
        String audience,
        String secret,
        Duration clockSkew,
        Duration devTokenTtl,
        List<String> allowedCorsOrigins
) {
}
