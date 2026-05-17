package dev.kavrin.banking_ledger.shared.error;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        Integer status,
        ApiErrorCode code,
        String message,
        String path,
        String correlationId,
        List<FieldError> fieldErrors
) {
    public static ApiErrorResponse of(
            Integer status,
            ApiErrorCode code,
            String message,
            String path,
            String correlationId
    ) {
        return of(status, code, message, path, correlationId, List.of());
    }

    public static ApiErrorResponse of(
            Integer status,
            ApiErrorCode code,
            String message,
            String path,
            String correlationId,
            List<FieldError> fieldErrors
    ) {
        return new ApiErrorResponse(Instant.now(), status, code, message, path, correlationId, fieldErrors);
    }

    public record FieldError(
            String field,
            String message
    ) {}
}
