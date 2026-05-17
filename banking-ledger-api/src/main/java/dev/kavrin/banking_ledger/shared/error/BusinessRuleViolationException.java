package dev.kavrin.banking_ledger.shared.error;

import org.springframework.http.HttpStatus;

public class BusinessRuleViolationException extends DomainException {

    public BusinessRuleViolationException(ApiErrorCode code, String message) {
        super(code, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    public BusinessRuleViolationException(ApiErrorCode code, String message, String publicMessage) {
        super(code, HttpStatus.UNPROCESSABLE_ENTITY, message, publicMessage);
    }
}
