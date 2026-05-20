package dev.kavrin.banking_ledger.reconciliation.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CreateSettlementBatchRequestTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void validRequestPassesBeanValidation() {
        var request = validRequest();

        var violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void missingRequiredFieldsReturnBeanValidationViolations() {
        var request = new CreateSettlementBatchRequest(
                "",
                "",
                "",
                "corr-1",
                List.of(new SettlementItemRequest(
                        "",
                        null,
                        "US1",
                        "",
                        null,
                        Map.of()
                ))
        );

        var violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(
                        "source",
                        "referenceName",
                        "importedByActor",
                        "items[0].externalTransactionReference",
                        "items[0].amountMinor",
                        "items[0].currencyCode",
                        "items[0].status",
                        "items[0].settlementDate"
                );
    }

    private CreateSettlementBatchRequest validRequest() {
        return new CreateSettlementBatchRequest(
                "VISA",
                "settlement-file.csv",
                "ops-1",
                "corr-1",
                List.of(new SettlementItemRequest(
                        "ext-1",
                        100L,
                        "USD",
                        "SETTLED",
                        LocalDate.of(2026, 5, 20),
                        Map.of("merchant", "demo")
                ))
        );
    }
}
