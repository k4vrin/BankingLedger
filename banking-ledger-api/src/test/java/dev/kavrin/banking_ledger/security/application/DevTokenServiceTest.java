package dev.kavrin.banking_ledger.security.application;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import dev.kavrin.banking_ledger.security.config.JwtSecurityProperties;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DevTokenServiceTest {

    private static final String SECRET = "12345678901234567890123456789012";
    private static final UUID CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void issueCreatesHs256TokenThatCanBeDecodedByConfiguredSecret() {
        var secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var service = new DevTokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(secretKey)),
                new JwtSecurityProperties(
                        "banking-ledger-local",
                        "banking-ledger-api",
                        SECRET,
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(15),
                        List.of("http://localhost:3000")
                )
        );
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        var response = service.issue(SecurityRole.CUSTOMER, "dev-customer", CUSTOMER_ID, "customer-user-1");
        var jwt = decoder.decode(response.accessToken());

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900);
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
        assertThat(jwt.getSubject()).isEqualTo("dev-customer");
        assertThat(jwt.getAudience()).containsExactly("banking-ledger-api");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getClaimAsString("actorId")).isEqualTo("customer-user-1");
        assertThat(jwt.getClaimAsString("customerId")).isEqualTo(CUSTOMER_ID.toString());
    }
}
