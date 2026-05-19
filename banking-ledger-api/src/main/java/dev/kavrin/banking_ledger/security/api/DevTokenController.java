package dev.kavrin.banking_ledger.security.api;

import dev.kavrin.banking_ledger.security.application.DevTokenService;
import dev.kavrin.banking_ledger.security.domain.SecurityRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Profile("dev")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dev/auth")
public class DevTokenController {

    private final DevTokenService devTokenService;

    @PostMapping("/tokens")
    public DevTokenResponse issueToken(@Valid @RequestBody DevTokenRequest request) {
        return devTokenService.issue(request.role(), request.subject(), request.customerId(), request.actorId());
    }

    public record DevTokenRequest(
            @NotNull SecurityRole role,
            String subject,
            UUID customerId,
            String actorId
    ) {
    }

    public record DevTokenResponse(
            String tokenType,
            String accessToken,
            long expiresInSeconds
    ) {
    }
}
