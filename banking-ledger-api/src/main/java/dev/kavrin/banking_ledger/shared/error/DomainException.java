package dev.kavrin.banking_ledger.shared.error;

import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {

    private final ApiErrorCode code;
    private final HttpStatus status;
    private final String publicMessage;

    protected DomainException(ApiErrorCode code, HttpStatus status, String publicMessage) {
        this(code, status, publicMessage, publicMessage);
    }

    protected DomainException(ApiErrorCode code, HttpStatus status, String message, String publicMessage) {
        super(message);
        this.code = code;
        this.status = status;
        this.publicMessage = publicMessage;
    }

    public ApiErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String publicMessage() {
        return publicMessage;
    }
}
