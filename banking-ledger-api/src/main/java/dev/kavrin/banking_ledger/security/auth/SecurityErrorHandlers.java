package dev.kavrin.banking_ledger.security.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.kavrin.banking_ledger.shared.correlation.CorrelationIdFilter;
import dev.kavrin.banking_ledger.shared.error.ApiErrorCode;
import dev.kavrin.banking_ledger.shared.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class SecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger logger = LoggerFactory.getLogger(SecurityErrorHandlers.class);

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        logger.warn(
                "Authentication failure path={} correlationId={}",
                request.getRequestURI(),
                CorrelationIdFilter.currentCorrelationId()
        );
        write(response, HttpStatus.UNAUTHORIZED, ApiErrorCode.Security.AUTHENTICATION_REQUIRED,
                "Authentication is required.", request.getRequestURI());
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.access.AccessDeniedException accessDeniedException
    ) throws IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        logger.warn(
                "Authorization failure subject={} roles={} path={} correlationId={}",
                authentication == null ? "anonymous" : authentication.getName(),
                authentication == null
                        ? "[]"
                        : authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList(),
                request.getRequestURI(),
                CorrelationIdFilter.currentCorrelationId()
        );
        write(response, HttpStatus.FORBIDDEN, ApiErrorCode.Security.ACCESS_DENIED,
                "Access is denied.", request.getRequestURI());
    }

    private void write(
            HttpServletResponse response,
            HttpStatus status,
            ApiErrorCode code,
            String message,
            String path
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                status.value(),
                code,
                message,
                path,
                CorrelationIdFilter.currentCorrelationId()
        ));
    }
}
