package dev.kavrin.banking_ledger.shared.error;

import org.springframework.http.HttpStatus;

public class SecurityDomainException extends DomainException {

    public SecurityDomainException(ApiErrorCode.Security code, HttpStatus status, String message) {
        super(code, status, message);
    }

    public SecurityDomainException(ApiErrorCode.Security code, HttpStatus status, String message, String publicMessage) {
        super(code, status, message, publicMessage);
    }

    public static SecurityDomainException unauthorized(ApiErrorCode.Security code, String message) {
        return new SecurityDomainException(code, HttpStatus.UNAUTHORIZED, message);
    }

    public static SecurityDomainException unauthorized(ApiErrorCode.Security code, String message, String publicMessage) {
        return new SecurityDomainException(code, HttpStatus.UNAUTHORIZED, message, publicMessage);
    }

    public static SecurityDomainException forbidden(ApiErrorCode.Security code, String message) {
        return new SecurityDomainException(code, HttpStatus.FORBIDDEN, message);
    }

    public static SecurityDomainException forbidden(ApiErrorCode.Security code, String message, String publicMessage) {
        return new SecurityDomainException(code, HttpStatus.FORBIDDEN, message, publicMessage);
    }
}
