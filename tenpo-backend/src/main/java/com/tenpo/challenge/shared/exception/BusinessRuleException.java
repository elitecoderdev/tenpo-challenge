package com.tenpo.challenge.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * EN: Unchecked domain exception used to signal that a business rule has been violated.
 *     The caller specifies the HTTP status that the violation should map to, giving the
 *     service layer control over the response code without depending on Spring MVC types
 *     for the status itself.
 *
 *     Examples of violations handled by this exception:
 *       - A Tenpista attempting to register a 101st transaction (HTTP 409 Conflict).
 *
 *     {@link GlobalExceptionHandler} catches this exception and builds a structured
 *     {@code ApiError} response with the supplied status and message.
 *
 * ES: Excepción de dominio no verificada usada para señalar que se ha violado una regla de negocio.
 *     El llamador especifica el estado HTTP al que debe mapearse la violación, dando a la capa
 *     de servicio control sobre el código de respuesta sin depender de los tipos de Spring MVC
 *     para el estado en sí.
 *
 *     Ejemplos de violaciones manejadas por esta excepción:
 *       - Un Tenpista intentando registrar la transacción número 101 (HTTP 409 Conflict).
 *
 *     {@link GlobalExceptionHandler} captura esta excepción y construye una respuesta
 *     {@code ApiError} estructurada con el estado y mensaje proporcionados.
 *
 * Design — SOLID:
 *   SRP : Carries only the violation message and the desired HTTP response status.
 *   OCP : New business rules throw this exception with any status — no handler changes needed.
 */
public class BusinessRuleException extends RuntimeException {

    // EN: The HTTP status code that the global exception handler should use when producing the response.
    //     Stored in the exception so the thrower controls the status (e.g. 409 Conflict vs 422 Unprocessable).
    // ES: El código de estado HTTP que el manejador global de excepciones debe usar al producir la respuesta.
    //     Almacenado en la excepción para que el lanzador controle el estado (ej. 409 Conflict vs 422 Unprocessable).
    private final HttpStatus status;

    /**
     * EN: Constructs a new business rule violation with a human-readable message and the desired status.
     *
     * ES: Construye una nueva violación de regla de negocio con un mensaje legible por humanos y el estado deseado.
     *
     * @param message descriptive reason for the violation / razón descriptiva de la violación
     * @param status  the HTTP response status to return / el estado de respuesta HTTP a devolver
     */
    public BusinessRuleException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    /**
     * EN: Returns the HTTP status that the global exception handler should use for the response.
     * ES: Devuelve el estado HTTP que el manejador global de excepciones debe usar para la respuesta.
     *
     * @return the HTTP status / el estado HTTP
     */
    public HttpStatus getStatus() {
        return status;
    }
}
