package com.tenpo.challenge.shared.api;

import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * EN: Factory bean that assembles {@link ApiError} instances.
 *     Extracting construction into a dedicated factory keeps {@link ApiError} as a pure
 *     value record and {@link com.tenpo.challenge.shared.exception.GlobalExceptionHandler}
 *     free of the details of building timestamps and deriving reason phrases.
 *     This also makes the factory easily replaceable or mockable in tests.
 *
 * ES: Bean fábrica que ensambla instancias de {@link ApiError}.
 *     Extraer la construcción a una fábrica dedicada mantiene {@link ApiError} como un registro
 *     de valor puro y {@link com.tenpo.challenge.shared.exception.GlobalExceptionHandler}
 *     libre de los detalles de construir timestamps y derivar frases de razón.
 *     Esto también hace que la fábrica sea fácilmente reemplazable o mockeable en tests.
 *
 * Design — SOLID:
 *   SRP : Only assembles ApiError objects; does not handle HTTP routing or business rules.
 *   DIP : GlobalExceptionHandler depends on this factory via constructor injection.
 */
@Component
public class ApiErrorFactory {

    /**
     * EN: Creates a fully populated {@link ApiError} for the given status, message, path, and field errors.
     *     The {@code timestamp} is captured at call time using {@code Instant.now()} so every
     *     error response reflects when it was actually generated.
     *     The {@code error} phrase is derived from the status enum to avoid mismatches.
     *
     * ES: Crea un {@link ApiError} completamente poblado para el estado, mensaje, ruta y errores de campo dados.
     *     El {@code timestamp} se captura en el momento de la llamada usando {@code Instant.now()} para que
     *     cada respuesta de error refleje cuándo fue generada realmente.
     *     La frase {@code error} se deriva del enum de estado para evitar inconsistencias.
     *
     * @param status      the HTTP status to embed / el estado HTTP a incrustar
     * @param message     the human-readable error description / la descripción de error legible por humanos
     * @param path        the request URI that triggered the error / la URI de solicitud que desencadenó el error
     * @param fieldErrors per-field violations (empty list if not applicable) / violaciones por campo (lista vacía si no aplica)
     * @return a fully populated ApiError record / un registro ApiError completamente poblado
     */
    public ApiError build(HttpStatus status, String message, String path, List<ApiFieldError> fieldErrors) {
        return new ApiError(
                // EN: Capture the current UTC instant to timestamp the error response.
                // ES: Capturamos el instante UTC actual para marcar con tiempo la respuesta de error.
                Instant.now(),
                status.value(),

                // EN: Use the standard HTTP reason phrase (e.g. "Bad Request") derived from the status enum.
                // ES: Usamos la frase de razón HTTP estándar (ej. "Bad Request") derivada del enum de estado.
                status.getReasonPhrase(),
                message,
                path,
                fieldErrors
        );
    }
}
