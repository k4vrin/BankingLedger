package dev.kavrin.banking_ledger.docs;

import dev.kavrin.banking_ledger.shared.error.ApiErrorResponse;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";
    private static final String CORRELATION_ID = "X-Correlation-Id";
    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    @Bean
    OpenAPI bankingLedgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Mini Core Banking Ledger API")
                        .version("0.0.1")
                        .description("""
                                Spring Boot portfolio API for double-entry ledger posting, internal transfers,
                                immutable reversals, operational adjustments, reconciliation, audit investigation,
                                and transactional outbox publishing.
                                """)
                        .contact(new Contact()
                                .name("Kavrin Banking Ledger")
                                .url("https://github.com/kavrin")))
                .servers(List.of(new Server()
                        .url("http://localhost:8080")
                        .description("Local development server")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT bearer token issued by `/api/v1/dev/auth/tokens` in the dev profile."))
                        .addExamples("validationError", errorExample(
                                400,
                                "VALIDATION_ERROR",
                                "Request validation failed",
                                "/api/v1/transfers",
                                List.of(new ApiErrorResponse.FieldError("amountMinor", "amountMinor must be positive"))))
                        .addExamples("authenticationError", errorExample(
                                401,
                                "AUTHENTICATION_REQUIRED",
                                "Authentication is required",
                                "/api/v1/transfers",
                                List.of()))
                        .addExamples("authorizationError", errorExample(
                                403,
                                "ACCESS_DENIED",
                                "Access is denied",
                                "/api/v1/ops/adjustments",
                                List.of()))
                        .addExamples("idempotencyConflict", errorExample(
                                409,
                                "IDEMPOTENCY_KEY_CONFLICT",
                                "Idempotency key was already used with a different request body",
                                "/api/v1/transfers",
                                List.of()))
                        .addExamples("concurrencyConflict", errorExample(
                                409,
                                "CONCURRENT_TRANSFER_CONFLICT",
                                "Concurrent account update detected. Retry the request.",
                                "/api/v1/transfers",
                                List.of()))
                        .addExamples("notFound", errorExample(
                                404,
                                "RESOURCE_NOT_FOUND",
                                "Resource was not found",
                                "/api/v1/accounts/00000000-0000-0000-0000-000000001001",
                                List.of()))
                        .addSchemas("ApiErrorResponse", apiErrorResponseSchema())
                        .addSchemas("FieldError", new Schema<>()
                                .type("object")
                                .addProperty("field", new Schema<String>().type("string").example("amountMinor"))
                                .addProperty("message", new Schema<String>().type("string").example("amountMinor must be positive"))))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    OperationCustomizer commonHeaderDocumentationCustomizer() {
        return (operation, handlerMethod) -> {
            if (isBusinessApi(handlerMethod)) {
                addCorrelationIdHeader(operation);
                addCommonErrorResponses(operation);
            }
            if (isTransferCreate(handlerMethod)) {
                addIdempotencyKeyHeader(operation);
                addTransferExamples(operation);
            }
            return operation;
        };
    }

    @Bean
    GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder()
                .group("customer")
                .pathsToMatch("/api/v1/accounts/**", "/api/v1/transfers/**")
                .build();
    }

    @Bean
    GroupedOpenApi opsApi() {
        return GroupedOpenApi.builder()
                .group("ops")
                .pathsToMatch("/api/v1/ops/**")
                .build();
    }

    @Bean
    GroupedOpenApi auditApi() {
        return GroupedOpenApi.builder()
                .group("audit")
                .pathsToMatch("/api/v1/audit/**")
                .build();
    }

    @Bean
    GroupedOpenApi devApi() {
        return GroupedOpenApi.builder()
                .group("dev")
                .pathsToMatch("/api/v1/dev/**")
                .build();
    }

    private boolean isBusinessApi(HandlerMethod handlerMethod) {
        Package controllerPackage = handlerMethod.getBeanType().getPackage();
        return controllerPackage != null
                && controllerPackage.getName().startsWith("dev.kavrin.banking_ledger")
                && !controllerPackage.getName().contains(".security.api");
    }

    private boolean isTransferCreate(HandlerMethod handlerMethod) {
        return handlerMethod.getBeanType().getSimpleName().equals("TransferController")
                && handlerMethod.getMethod().getName().equals("createTransfer");
    }

    private void addCorrelationIdHeader(Operation operation) {
        addHeader(operation, CORRELATION_ID, false,
                "Client supplied correlation id. If omitted where optional, the API generates one and returns it in the response header.");
    }

    private void addIdempotencyKeyHeader(Operation operation) {
        addHeader(operation, IDEMPOTENCY_KEY, false,
                "Required for transfer creation. Reuse the same key for safe retries of the same request body.");
    }

    private void addHeader(Operation operation, String name, boolean required, String description) {
        List<Parameter> parameters = operation.getParameters();
        if (parameters != null && parameters.stream().anyMatch(parameter -> name.equalsIgnoreCase(parameter.getName()))) {
            return;
        }
        operation.addParametersItem(new Parameter()
                .in("header")
                .name(name)
                .required(required)
                .description(description)
                .schema(new Schema<String>().type("string")));
    }

    private void addCommonErrorResponses(Operation operation) {
        operation.getResponses().addApiResponse("400", errorResponse("Validation or malformed request error.", "validationError"));
        operation.getResponses().addApiResponse("401", errorResponse("Missing, expired, or invalid bearer token.", "authenticationError"));
        operation.getResponses().addApiResponse("403", errorResponse("Authenticated principal is not allowed to perform the operation.", "authorizationError"));
        operation.getResponses().addApiResponse("404", errorResponse("Requested resource does not exist.", "notFound"));
        operation.getResponses().addApiResponse("409", errorResponse("Business, idempotency, or concurrency conflict.", "idempotencyConflict"));
    }

    private void addTransferExamples(Operation operation) {
        if (operation.getRequestBody() != null) {
            Content requestContent = operation.getRequestBody().getContent();
            if (requestContent == null) {
                requestContent = new Content();
                operation.getRequestBody().content(requestContent);
            }
            MediaType jsonMediaType = requestContent.get(org.springframework.http.MediaType.APPLICATION_JSON_VALUE);
            if (jsonMediaType == null) {
                jsonMediaType = new MediaType();
                requestContent.addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, jsonMediaType);
            }
            jsonMediaType.addExamples("successfulTransfer", new Example()
                    .summary("Successful transfer request")
                    .value("""
                            {
                              "sourceAccountId": "00000000-0000-0000-0000-000000001001",
                              "destinationAccountId": "00000000-0000-0000-0000-000000001002",
                              "currencyCode": "USD",
                              "amountMinor": 1500,
                              "externalReference": "demo-transfer-001",
                              "description": "Demo transfer"
                            }
                            """));
        }
        operation.getResponses().addApiResponse("201", new io.swagger.v3.oas.models.responses.ApiResponse()
                .description("Transfer created.")
                .content(jsonContent(new Example()
                        .summary("Created transfer")
                        .value("""
                                {
                                  "id": "00000000-0000-0000-0000-000000005001",
                                  "sourceAccountId": "00000000-0000-0000-0000-000000001001",
                                  "destinationAccountId": "00000000-0000-0000-0000-000000001002",
                                  "status": "COMPLETED",
                                  "currencyCode": "USD",
                                  "amountMinor": 1500,
                                  "externalReference": "demo-transfer-001"
                                }
                                """))));
        operation.getResponses().addApiResponse("200", new io.swagger.v3.oas.models.responses.ApiResponse()
                .description("Idempotent replay of an already completed transfer.")
                .content(jsonContent(new Example()
                        .summary("Idempotency replay")
                        .value("""
                                {
                                  "id": "00000000-0000-0000-0000-000000005001",
                                  "status": "COMPLETED",
                                  "externalReference": "demo-transfer-001"
                                }
                                """))));
    }

    private io.swagger.v3.oas.models.responses.ApiResponse errorResponse(String description, String exampleName) {
        return new io.swagger.v3.oas.models.responses.ApiResponse()
                .description(description)
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                        new MediaType()
                                .schema(new Schema<>().$ref("#/components/schemas/ApiErrorResponse"))
                                .addExamples(exampleName, new Example().$ref("#/components/examples/" + exampleName))));
    }

    private Schema<ApiErrorResponse> apiErrorResponseSchema() {
        return new Schema<ApiErrorResponse>()
                .type("object")
                .description("Standard structured error response returned by validation, security, domain, and infrastructure handlers.")
                .addProperty("timestamp", new Schema<String>().type("string").format("date-time"))
                .addProperty("status", new Schema<Integer>().type("integer").format("int32").example(400))
                .addProperty("code", new Schema<String>().type("string").example("VALIDATION_ERROR"))
                .addProperty("message", new Schema<String>().type("string").example("Request validation failed"))
                .addProperty("path", new Schema<String>().type("string").example("/api/v1/transfers"))
                .addProperty("correlationId", new Schema<String>().type("string").example("demo-correlation-id"))
                .addProperty("fieldErrors", new Schema<>()
                        .type("array")
                        .items(new Schema<>().$ref("#/components/schemas/FieldError")));
    }

    private Content jsonContent(Example example) {
        return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                new MediaType().addExamples(example.getSummary(), example));
    }

    private Example errorExample(
            int status,
            String code,
            String message,
            String path,
            List<ApiErrorResponse.FieldError> fieldErrors
    ) {
        return new Example().value(new ErrorExampleValue(
                "2026-05-21T10:15:30Z",
                status,
                code,
                message,
                path,
                "demo-correlation-id",
                fieldErrors
        ));
    }

    private record ErrorExampleValue(
            String timestamp,
            int status,
            String code,
            String message,
            String path,
            String correlationId,
            List<ApiErrorResponse.FieldError> fieldErrors
    ) {
    }
}
