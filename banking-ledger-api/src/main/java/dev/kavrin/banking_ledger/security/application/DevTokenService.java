package dev.kavrin.banking_ledger.security.application;

import dev.kavrin.banking_ledger.security.api.DevTokenController.DevTokenResponse;
import dev.kavrin.banking_ledger.security.config.JwtSecurityProperties;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Profile("dev")
@Service
@RequiredArgsConstructor
public class DevTokenService {

    private static final UUID SAMPLE_CUSTOMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final JwtEncoder jwtEncoder;
    private final JwtSecurityProperties properties;

    public DevTokenResponse issue(SecurityRole role, String subject, UUID customerId, String actorId) {
        if (role == SecurityRole.CUSTOMER && customerId == null) {
            throw new BadRequestException(
                    ApiErrorCode.Validation.INVALID_REQUEST,
                    "customerId is required for CUSTOMER tokens",
                    "customerId is required for CUSTOMER tokens"
            );
        }

        var now = Instant.now();
        var ttl = properties.accessTokenTtl();
        var effectiveSubject = subject == null || subject.isBlank()
                ? "dev-" + role.name().toLowerCase().replace('_', '-')
                : subject.trim();

        var claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .subject(effectiveSubject)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(role.name()))
                .claim("actorId", actorId == null || actorId.isBlank() ? effectiveSubject : actorId.trim());

        if (customerId != null) {
            claims.claim("customerId", customerId.toString());
        }

        return new DevTokenResponse("Bearer", jwtEncoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue(), ttl.toSeconds());
    }

    public List<SampleDevUser> sampleUsers() {
        return List.of(
                new SampleDevUser(SecurityRole.CUSTOMER, "dev-customer", "customer-user-1", SAMPLE_CUSTOMER_ID),
                new SampleDevUser(SecurityRole.TELLER, "dev-teller", "teller-user-1", null),
                new SampleDevUser(SecurityRole.AUDITOR, "dev-auditor", "auditor-user-1", null),
                new SampleDevUser(SecurityRole.OPS_ADMIN, "dev-ops-admin", "ops-user-1", null),
                new SampleDevUser(SecurityRole.SERVICE, "dev-service", "service-client-1", null)
        );
    }

    public record SampleDevUser(
            SecurityRole role,
            String subject,
            String actorId,
            UUID customerId
    ) {
    }
}
