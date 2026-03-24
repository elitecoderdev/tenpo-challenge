package com.tenpo.challenge.shared.exception;

/**
 * EN: Unchecked domain exception thrown when a requested resource does not exist in the system.
 *     Always maps to HTTP 404 Not Found via {@link GlobalExceptionHandler}.
 *     Using a dedicated exception type (instead of a generic RuntimeException) makes the
 *     intent explicit at both the throw site and the handler, and allows catch clauses
 *     to distinguish "not found" from "unexpected error" cleanly.
 *
 * ES: Excepción de dominio no verificada lanzada cuando un recurso solicitado no existe en el sistema.
 *     Siempre mapea a HTTP 404 Not Found via {@link GlobalExceptionHandler}.
 *     Usar un tipo de excepción dedicado (en lugar de un RuntimeException genérico) hace que
 *     la intención sea explícita tanto en el sitio de lanzamiento como en el manejador, y permite
 *     que las cláusulas catch distingan "no encontrado" de "error inesperado" limpiamente.
 *
 * Design — SOLID:
 *   SRP : Models only the "resource not found" failure mode; no status logic (always 404).
 *   OCP : Any service method can throw this for any resource type without modifying the handler.
 */
public class ResourceNotFoundException extends RuntimeException {

    /**
     * EN: Constructs the exception with a message describing which resource was not found.
     *     The message is forwarded verbatim to the API error response so callers can
     *     understand exactly what was missing (e.g. "Transaction with id 44 was not found.").
     *
     * ES: Construye la excepción con un mensaje describiendo qué recurso no fue encontrado.
     *     El mensaje se reenvía literalmente a la respuesta de error de la API para que los
     *     llamadores puedan entender exactamente qué faltaba (ej. "Transaction with id 44 was not found.").
     *
     * @param message the human-readable description of the missing resource
     *                / la descripción legible por humanos del recurso faltante
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
