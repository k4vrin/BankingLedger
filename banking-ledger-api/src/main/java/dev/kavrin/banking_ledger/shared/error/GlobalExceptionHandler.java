package dev.kavrin.banking_ledger.shared.error;

import dev.kavrin.banking_ledger.shared.correlation.CorrelationIdFilter;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiErrorResponse> handleDomainException(DomainException exception, HttpServletRequest request) {
        log.warn("Domain exception: {}", exception.getMessage());
        return response(exception.status(), exception.code(), exception.publicMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.FieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new ApiErrorResponse.FieldError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()
                ))
                .toList();

        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.Validation.VALIDATION_ERROR,
                "Request validation failed.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ApiErrorResponse.FieldError> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ApiErrorResponse.FieldError(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return response(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.Validation.VALIDATION_ERROR,
                "Request validation failed.",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return response(
                HttpStatus.UNAUTHORIZED,
                ApiErrorCode.Security.AUTHENTICATION_REQUIRED,
                "Authentication is required.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return response(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.Security.ACCESS_DENIED,
                "Access is denied.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNoHandlerFound(NoHandlerFoundException exception, HttpServletRequest request) {
        return response(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.Business.RESOURCE_NOT_FOUND,
                "Resource not found.",
                request,
                List.of()
        );
    }

    @ExceptionHandler({
            CannotAcquireLockException.class,
            PessimisticLockingFailureException.class,
            LockTimeoutException.class
    })
    ResponseEntity<ApiErrorResponse> handlePessimisticLockFailure(Exception exception, HttpServletRequest request) {
        log.warn(
                "Retryable pessimistic locking failure. correlationId={}, error={}",
                CorrelationIdFilter.currentCorrelationId(),
                exception.getClass().getSimpleName()
        );

        return response(
                HttpStatus.CONFLICT,
                ApiErrorCode.Business.CONCURRENT_TRANSFER_CONFLICT,
                "Transfer could not be completed because the account is currently being modified. Please retry.",
                request,
                List.of()
        );
    }

    @ExceptionHandler({
            OptimisticLockingFailureException.class,
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    ResponseEntity<ApiErrorResponse> handleOptimisticLockFailure(Exception exception, HttpServletRequest request) {
        log.warn(
                "Retryable optimistic locking failure. correlationId={}, error={}",
                CorrelationIdFilter.currentCorrelationId(),
                exception.getClass().getSimpleName()
        );

        return response(
                HttpStatus.CONFLICT,
                ApiErrorCode.Business.CONCURRENT_TRANSFER_CONFLICT,
                "Transfer could not be completed because the account changed during processing. Please retry.",
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception", exception);
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ApiErrorCode.Infrastructure.INTERNAL_ERROR,
                "An unexpected error occurred.",
                request,
                List.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            ApiErrorCode code,
            String message,
            HttpServletRequest request,
            List<ApiErrorResponse.FieldError> fieldErrors
    ) {
        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(
                        status.value(),
                        code,
                        message,
                        request.getRequestURI(),
                        CorrelationIdFilter.currentCorrelationId(),
                        fieldErrors
                ));
    }
}
