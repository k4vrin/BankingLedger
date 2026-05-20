package dev.kavrin.banking_ledger.security.support;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TestJwtFactory {

    private final String issuer;
    private final String audience;
    private final String secret;
    private final String wrongSecret;

    public TestJwtFactory(String issuer, String audience, String secret, String wrongSecret) {
        this.issuer = issuer;
        this.audience = audience;
        this.secret = secret;
        this.wrongSecret = wrongSecret;
    }

    public String customerToken(UUID customerId) {
        return token(secret, List.of(SecurityRole.CUSTOMER.name()), "customer-subject", customerId,
                Instant.now(), Instant.now().plusSeconds(3600), null, issuer, audience);
    }

    public String roleToken(SecurityRole role) {
        return token(secret, List.of(role.name()), role.name().toLowerCase() + "-subject", null,
                Instant.now(), Instant.now().plusSeconds(3600), null, issuer, audience);
    }

    public String expiredCustomerToken(UUID customerId) {
        return token(secret, List.of(SecurityRole.CUSTOMER.name()), "customer-subject", customerId,
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(60), null, issuer, audience);
    }

    public String notBeforeCustomerToken(UUID customerId) {
        return token(secret, List.of(SecurityRole.CUSTOMER.name()), "customer-subject", customerId,
                Instant.now(), Instant.now().plusSeconds(3600), Instant.now().plusSeconds(3600), issuer, audience);
    }

    public String invalidIssuerCustomerToken(UUID customerId) {
        return token(secret, List.of(SecurityRole.CUSTOMER.name()), "customer-subject", customerId,
                Instant.now(), Instant.now().plusSeconds(3600), null, "wrong-issuer", audience);
    }

    public String invalidAudienceCustomerToken(UUID customerId) {
        return token(secret, List.of(SecurityRole.CUSTOMER.name()), "customer-subject", customerId,
                Instant.now(), Instant.now().plusSeconds(3600), null, issuer, "wrong-audience");
    }

    public String wrongKeyCustomerToken(UUID customerId) {
        return token(wrongSecret, List.of(SecurityRole.CUSTOMER.name()), "customer-subject", customerId,
                Instant.now(), Instant.now().plusSeconds(3600), null, issuer, audience);
    }

    public String unrecognizedRoleToken() {
        return token(secret, List.of("UNKNOWN"), "unknown-subject", null,
                Instant.now(), Instant.now().plusSeconds(3600), null, issuer, audience);
    }

    private String token(
            String signingSecret,
            List<String> roles,
            String subject,
            UUID customerId,
            Instant issuedAt,
            Instant expiresAt,
            Instant notBefore,
            String tokenIssuer,
            String tokenAudience
    ) {
        var claims = JwtClaimsSet.builder()
                .issuer(tokenIssuer)
                .audience(List.of(tokenAudience))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(subject)
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .claim("actorId", subject + "-actor");

        if (customerId != null) {
            claims.claim("customerId", customerId.toString());
        }
        if (notBefore != null) {
            claims.notBefore(notBefore);
        }

        var key = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key.getEncoded()));
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
