package dev.kavrin.banking_ledger.shared.error;

import org.springframework.http.HttpStatus;

public class BadRequestException extends DomainException {

    public BadRequestException(String message) {
        this(ApiErrorCode.Validation.INVALID_REQUEST, message);
    }

    public BadRequestException(ApiErrorCode code, String message) {
        super(code, HttpStatus.BAD_REQUEST, message);
    }

    public BadRequestException(ApiErrorCode code, String message, String publicMessage) {
        super(code, HttpStatus.BAD_REQUEST, message, publicMessage);
    }
}
