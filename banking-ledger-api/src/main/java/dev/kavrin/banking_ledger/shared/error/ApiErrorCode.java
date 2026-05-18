package dev.kavrin.banking_ledger.shared.error;

import com.fasterxml.jackson.annotation.JsonValue;

public sealed interface ApiErrorCode permits
        ApiErrorCode.Business,
        ApiErrorCode.Security,
        ApiErrorCode.Validation,
        ApiErrorCode.Infrastructure {

    @JsonValue
    default String code() {
        return ((Enum<?>) this).name();
    }

    enum Business implements ApiErrorCode {
        ACCOUNT_ALREADY_VERIFIED,
        TOO_MANY_VERIFICATION_ATTEMPTS,
        VERIFICATION_CODE_EXPIRED,
        INVALID_VERIFICATION_CODE,
        ACCOUNT_DISABLED,
        ACCOUNT_NOT_VERIFIED,
        EMAIL_ALREADY_REGISTERED,
        PHONE_ALREADY_REGISTERED,
        RESOURCE_NOT_FOUND,
        ACCOUNT_NOT_FOUND,
        CUSTOMER_NOT_FOUND,
        ACCOUNT_NUMBER_ALREADY_EXISTS,
        INVALID_ACCOUNT_TYPE,
        INVALID_ACCOUNT_STATUS,
        DIARY_ENTRY_LOCKED,
        SUBSCRIPTION_REQUIRED,
        SUBSCRIPTION_EXPIRED,
        DUPLICATE_REQUEST,
        IDEMPOTENCY_KEY_CONFLICT,
        INVALID_IDEMPOTENCY_KEY
    }

    enum Security implements ApiErrorCode {
        INVALID_CREDENTIALS,
        AUTHENTICATION_REQUIRED,
        ACCESS_DENIED,
        FORBIDDEN_RESOURCE,
        INVALID_ACCESS_TOKEN,
        EXPIRED_ACCESS_TOKEN,
        INSUFFICIENT_SCOPE,
        ACCOUNT_LOCKED,
        SUSPICIOUS_ACTIVITY_DETECTED
    }

    enum Validation implements ApiErrorCode {
        VALIDATION_ERROR,
        INVALID_REQUEST,
        MALFORMED_REQUEST
    }

    enum Infrastructure implements ApiErrorCode {
        EXTERNAL_SERVICE_UNAVAILABLE,
        RATE_LIMIT_EXCEEDED,
        INTERNAL_ERROR
    }
}
