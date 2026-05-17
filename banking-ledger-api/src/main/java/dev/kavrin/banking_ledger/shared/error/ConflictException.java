package dev.kavrin.banking_ledger.shared.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends DomainException {

    public ConflictException(ApiErrorCode code, String message) {
        super(code, HttpStatus.CONFLICT, message);
    }

    public ConflictException(ApiErrorCode code, String message, String publicMessage) {
        super(code, HttpStatus.CONFLICT, message, publicMessage);
    }
}
