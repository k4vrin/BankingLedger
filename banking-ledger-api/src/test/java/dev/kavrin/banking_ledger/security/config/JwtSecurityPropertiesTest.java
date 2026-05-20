package dev.kavrin.banking_ledger.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSecurityPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsRequiredSecuritySettings() {
        contextRunner
                .withPropertyValues(
                        "banking-ledger.security.jwt.issuer=issuer",
                        "banking-ledger.security.jwt.audience=audience",
                        "banking-ledger.security.jwt.secret=12345678901234567890123456789012",
                        "banking-ledger.security.jwt.clock-skew=30s",
                        "banking-ledger.security.jwt.access-token-ttl=15m",
                        "banking-ledger.security.jwt.allowed-cors-origins[0]=http://localhost:3000"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(JwtSecurityProperties.class);
                    assertThat(properties.issuer()).isEqualTo("issuer");
                    assertThat(properties.audience()).isEqualTo("audience");
                    assertThat(properties.clockSkew()).isEqualTo(Duration.ofSeconds(30));
                    assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.allowedCorsOrigins()).containsExactly("http://localhost:3000");
                });
    }

    @Test
    void failsWhenRequiredSecuritySettingsAreMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenSecretIsTooShort() {
        contextRunner
                .withPropertyValues(
                        "banking-ledger.security.jwt.issuer=issuer",
                        "banking-ledger.security.jwt.audience=audience",
                        "banking-ledger.security.jwt.secret=too-short",
                        "banking-ledger.security.jwt.clock-skew=30s",
                        "banking-ledger.security.jwt.access-token-ttl=15m",
                        "banking-ledger.security.jwt.allowed-cors-origins[0]=http://localhost:3000"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration
    @EnableConfigurationProperties(JwtSecurityProperties.class)
    static class PropertiesConfiguration {
    }
}
