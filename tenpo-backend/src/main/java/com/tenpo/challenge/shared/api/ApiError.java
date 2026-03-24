package com.tenpo.challenge.shared.api;

import java.time.Instant;
import java.util.List;

/**
 * EN: Immutable record that models the structured error payload returned by the API
 *     for every non-2xx response.
 *     Using a {@code record} provides a concise, immutable value object without
 *     requiring manually-written constructors or accessors.
 *
 *     JSON representation example:
 *     <pre>{@code
 *     {
 *       "timestamp": "2026-03-07T00:00:00Z",
 *       "status": 400,
 *       "error": "Bad Request",
 *       "message": "Validation failed for the request body.",
 *       "path": "/api/transactions",
 *       "fieldErrors": [
 *         { "field": "amountInPesos", "message": "Transaction amount cannot be negative." }
 *       ]
 *     }
 *     }</pre>
 *
 * ES: Registro inmutable que modela el payload de error estructurado devuelto por la API
 *     para cada respuesta no-2xx.
 *     Usar un {@code record} proporciona un objeto de valor conciso e inmutable sin requerir
 *     constructores o accesores escritos manualmente.
 *
 * Design — SOLID:
 *   SRP : Carries only the API error schema; no building or mapping logic.
 */
public record ApiError(

        // EN: UTC instant when the error was generated — helps correlate client logs with server logs.
        // ES: Instante UTC cuando se generó el error — ayuda a correlacionar logs del cliente con logs del servidor.
        Instant timestamp,

        // EN: Numeric HTTP status code (e.g. 400, 404, 500).
        // ES: Código de estado HTTP numérico (ej. 400, 404, 500).
        int status,

        // EN: Short human-readable phrase for the status (e.g. "Bad Request", "Not Found").
        //     Derived from the HttpStatus enum's reason phrase to keep it consistent.
        // ES: Frase corta legible por humanos para el estado (ej. "Bad Request", "Not Found").
        //     Derivada de la frase de razón del enum HttpStatus para mantenerla consistente.
        String error,

        // EN: Descriptive message explaining what went wrong.
        //     For generic errors this is a safe, non-leaking message.
        //     For validation errors this identifies the failed constraint class.
        // ES: Mensaje descriptivo que explica qué salió mal.
        //     Para errores genéricos es un mensaje seguro que no filtra información.
        //     Para errores de validación identifica la clase de restricción fallida.
        String message,

        // EN: The request URI that produced the error — aids debugging and logging.
        // ES: La URI de solicitud que produjo el error — ayuda al debugging y logging.
        String path,

        // EN: List of per-field validation failures. Empty for errors not related to validation.
        //     Populated with ApiFieldError entries so the frontend can display
        //     inline error messages next to the specific form fields that failed.
        // ES: Lista de fallos de validación por campo. Vacía para errores no relacionados con validación.
        //     Poblada con entradas ApiFieldError para que el frontend pueda mostrar
        //     mensajes de error inline junto a los campos de formulario específicos que fallaron.
        List<ApiFieldError> fieldErrors
) {
}
