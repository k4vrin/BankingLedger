package dev.kavrin.banking_ledger.shared.error;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String message) {
        super(ApiErrorCode.Business.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    public ResourceNotFoundException(String message, String publicMessage) {
        super(ApiErrorCode.Business.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message, publicMessage);
    }

    public ResourceNotFoundException(ApiErrorCode errorCode, String message, String publicMessage) {
        super(errorCode, HttpStatus.NOT_FOUND, message, publicMessage);
    }
}
