package com.tenpo.challenge.shared.api;

/**
 * EN: Immutable record representing a single field-level validation failure within an
 *     {@link ApiError} response.
 *     Populated from Spring's {@code FieldError} objects (bean validation on request bodies)
 *     and from Jakarta {@code ConstraintViolation} objects (path/query parameter constraints).
 *
 *     JSON representation:
 *     <pre>{@code
 *     { "field": "amountInPesos", "message": "Transaction amount cannot be negative." }
 *     }</pre>
 *
 *     The frontend maps these by {@code field} name back to the corresponding React Hook Form
 *     field using {@code form.setError(fieldName, { message: fieldError.message })}.
 *
 * ES: Registro inmutable que representa un único fallo de validación a nivel de campo dentro de
 *     una respuesta {@link ApiError}.
 *     Poblado desde los objetos {@code FieldError} de Spring (validación de bean en cuerpos de solicitud)
 *     y desde los objetos {@code ConstraintViolation} de Jakarta (restricciones de parámetros path/query).
 *
 *     El frontend mapea estos por nombre de {@code field} de vuelta al campo correspondiente
 *     de React Hook Form usando {@code form.setError(fieldName, { message: fieldError.message })}.
 *
 * Design — SOLID:
 *   SRP : Carries only the field name and failure message; no logic.
 */
public record ApiFieldError(

        // EN: The name of the field that failed validation (e.g. "amountInPesos", "transactionDate").
        //     Must match the JSON field name in the request body or the DTO accessor name.
        // ES: El nombre del campo que falló la validación (ej. "amountInPesos", "transactionDate").
        //     Debe coincidir con el nombre del campo JSON en el cuerpo de la solicitud o el nombre del accesor del DTO.
        String field,

        // EN: The human-readable constraint violation message from the annotation
        //     (e.g. "Transaction amount cannot be negative.").
        //     Used directly in the frontend form to display inline field errors.
        // ES: El mensaje de violación de restricción legible por humanos de la anotación
        //     (ej. "Transaction amount cannot be negative.").
        //     Usado directamente en el formulario del frontend para mostrar errores de campo inline.
        String message
) {
}
