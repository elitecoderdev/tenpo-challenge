package com.tenpo.challenge.shared.exception;

import com.tenpo.challenge.shared.api.ApiError;
import com.tenpo.challenge.shared.api.ApiErrorFactory;
import com.tenpo.challenge.shared.api.ApiFieldError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * EN: Centralized exception handler for all REST controllers in the application.
 *     {@code @RestControllerAdvice} registers this class as an AOP-based interceptor
 *     that catches exceptions thrown by any controller method and translates them into
 *     structured {@link ApiError} HTTP responses.
 *
 *     This design ensures that no controller needs try/catch blocks for common error
 *     scenarios, keeping controllers focused on happy-path logic (Open/Closed Principle).
 *
 *     Handled scenarios and their HTTP mappings:
 *       ResourceNotFoundException      → 404 Not Found
 *       BusinessRuleException          → caller-specified status (e.g. 409 Conflict)
 *       MethodArgumentNotValidException → 400 Bad Request (bean validation)
 *       ConstraintViolationException   → 400 Bad Request (constraint on query/path params)
 *       HttpMessageNotReadableException → 400 Bad Request (malformed JSON / wrong date format)
 *       Exception (catch-all)          → 500 Internal Server Error
 *
 * ES: Manejador de excepciones centralizado para todos los controladores REST de la aplicación.
 *     {@code @RestControllerAdvice} registra esta clase como un interceptor basado en AOP
 *     que captura excepciones lanzadas por cualquier método de controlador y las traduce en
 *     respuestas HTTP {@link ApiError} estructuradas.
 *
 *     Este diseño asegura que ningún controlador necesite bloques try/catch para escenarios
 *     de error comunes, manteniendo los controladores enfocados en la lógica del camino feliz
 *     (Principio Abierto/Cerrado).
 *
 *     Escenarios manejados y sus mapeos HTTP:
 *       ResourceNotFoundException      → 404 Not Found
 *       BusinessRuleException          → estado especificado por el llamador (ej. 409 Conflict)
 *       MethodArgumentNotValidException → 400 Bad Request (bean validation)
 *       ConstraintViolationException   → 400 Bad Request (restricción en query/path params)
 *       HttpMessageNotReadableException → 400 Bad Request (JSON malformado / formato de fecha incorrecto)
 *       Exception (catch-all)          → 500 Internal Server Error
 *
 * Design — SOLID:
 *   SRP : Handles only cross-cutting HTTP error translation; no business logic.
 *   OCP : New exception types can be handled by adding new @ExceptionHandler methods.
 *   DIP : Depends on ApiErrorFactory to build the response body (not hard-coded).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // EN: Factory injected via constructor to build consistent ApiError payloads.
    //     Extracting the builder keeps this class free of timestamp and serialization concerns.
    // ES: Fábrica inyectada via constructor para construir payloads ApiError consistentes.
    //     Extraer el builder mantiene esta clase libre de preocupaciones de timestamp y serialización.
    private final ApiErrorFactory apiErrorFactory;

    public GlobalExceptionHandler(ApiErrorFactory apiErrorFactory) {
        this.apiErrorFactory = apiErrorFactory;
    }

    // ── 404 Not Found ─────────────────────────────────────────────────────────────────────

    /**
     * EN: Handles {@link ResourceNotFoundException} — thrown when a requested entity is absent.
     *     Returns HTTP 404 with the exception message as the error description.
     *
     * ES: Maneja {@link ResourceNotFoundException} — lanzada cuando una entidad solicitada está ausente.
     *     Devuelve HTTP 404 con el mensaje de la excepción como descripción del error.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, exception.getMessage(), request.getRequestURI(), List.of());
    }

    // ── Business Rule Violation (caller-specified status) ─────────────────────────────────

    /**
     * EN: Handles {@link BusinessRuleException} — thrown when a domain rule prevents the operation.
     *     The status code is taken from the exception itself, allowing each rule to use
     *     the most semantically appropriate HTTP status (e.g. 409 Conflict for quota violations).
     *
     * ES: Maneja {@link BusinessRuleException} — lanzada cuando una regla de dominio impide la operación.
     *     El código de estado se toma de la excepción misma, permitiendo que cada regla use
     *     el estado HTTP semánticamente más apropiado (ej. 409 Conflict para violaciones de cuota).
     */
    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException exception, HttpServletRequest request) {
        return buildResponse(exception.getStatus(), exception.getMessage(), request.getRequestURI(), List.of());
    }

    // ── 400 Bean Validation (@Valid on @RequestBody) ──────────────────────────────────────

    /**
     * EN: Handles {@link MethodArgumentNotValidException} — thrown by Spring when a
     *     {@code @Valid} annotated request body fails Bean Validation.
     *     Extracts individual field errors and includes them in the response body
     *     so the frontend can map them back to specific form fields.
     *
     * ES: Maneja {@link MethodArgumentNotValidException} — lanzada por Spring cuando un
     *     cuerpo de solicitud anotado con {@code @Valid} falla Bean Validation.
     *     Extrae errores de campo individuales y los incluye en el cuerpo de la respuesta
     *     para que el frontend pueda mapearlos de vuelta a campos de formulario específicos.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        // EN: Collect all field-level constraint violations into a list of ApiFieldError records.
        // ES: Recopilamos todas las violaciones de restricción a nivel de campo en una lista de registros ApiFieldError.
        List<ApiFieldError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::mapFieldError)
                .toList();

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failed for the request body.",
                request.getRequestURI(),
                fieldErrors
        );
    }

    // ── 400 Constraint Violation (query/path params) ──────────────────────────────────────

    /**
     * EN: Handles {@link ConstraintViolationException} — thrown when constraints on
     *     method parameters (query params, path variables) fail validation.
     *     Converts each violation to an {@link ApiFieldError} using the property path and message.
     *
     * ES: Maneja {@link ConstraintViolationException} — lanzada cuando las restricciones en
     *     parámetros de método (query params, path variables) fallan la validación.
     *     Convierte cada violación a un {@link ApiFieldError} usando la ruta de propiedad y el mensaje.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<ApiFieldError> fieldErrors = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ApiFieldError(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();

        return buildResponse(HttpStatus.BAD_REQUEST, "Validation failed for request parameters.", request.getRequestURI(), fieldErrors);
    }

    // ── 400 Malformed JSON / Unreadable Payload ────────────────────────────────────────────

    /**
     * EN: Handles {@link HttpMessageNotReadableException} — thrown when the request body
     *     cannot be deserialized (e.g. malformed JSON, wrong date format, unexpected type).
     *     Returns a generic 400 message instead of the raw Jackson exception to avoid
     *     leaking internal serialization details to clients.
     *
     * ES: Maneja {@link HttpMessageNotReadableException} — lanzada cuando el cuerpo de la solicitud
     *     no puede ser deserializado (ej. JSON malformado, formato de fecha incorrecto, tipo inesperado).
     *     Devuelve un mensaje genérico 400 en lugar de la excepción Jackson cruda para evitar
     *     filtrar detalles internos de serialización a los clientes.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableMessage(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Request payload could not be parsed. Check field formats and required values.",
                request.getRequestURI(),
                List.of()
        );
    }

    // ── 500 Catch-All ─────────────────────────────────────────────────────────────────────

    /**
     * EN: Last-resort handler that catches any exception not matched by a more specific handler.
     *     Returns 500 Internal Server Error with a generic message to avoid exposing stack
     *     traces or internal implementation details to external clients (security best practice).
     *
     * ES: Manejador de último recurso que captura cualquier excepción no coincidida por un manejador más específico.
     *     Devuelve 500 Internal Server Error con un mensaje genérico para evitar exponer stack
     *     traces o detalles internos de implementación a clientes externos (buena práctica de seguridad).
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedError(Exception exception, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.",
                request.getRequestURI(),
                List.of()
        );
    }

    // ── Private Helpers ───────────────────────────────────────────────────────────────────

    /**
     * EN: Builds a {@link ResponseEntity} with the given status and an {@link ApiError} body.
     *     Delegates construction of the body to {@link ApiErrorFactory} to avoid duplicating
     *     the timestamp and status-to-reason-phrase mapping logic here (DRY principle).
     *
     * ES: Construye un {@link ResponseEntity} con el estado dado y un cuerpo {@link ApiError}.
     *     Delega la construcción del cuerpo a {@link ApiErrorFactory} para evitar duplicar
     *     la lógica de timestamp y mapeo de estado a frase de razón aquí (principio DRY).
     */
    private ResponseEntity<ApiError> buildResponse(
            HttpStatus status,
            String message,
            String path,
            List<ApiFieldError> fieldErrors
    ) {
        return ResponseEntity.status(status).body(apiErrorFactory.build(status, message, path, fieldErrors));
    }

    /**
     * EN: Converts a Spring {@link FieldError} (from bean validation) into the API's
     *     {@link ApiFieldError} record, extracting the field name and default message.
     *
     * ES: Convierte un {@link FieldError} de Spring (de bean validation) en el registro
     *     {@link ApiFieldError} de la API, extrayendo el nombre del campo y el mensaje por defecto.
     */
    private ApiFieldError mapFieldError(FieldError fieldError) {
        return new ApiFieldError(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
