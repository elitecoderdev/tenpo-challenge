package com.tenpo.challenge.rate;

import java.time.Duration;

/**
 * EN: Immutable value object representing the outcome of a rate-limit check for a single request.
 *     Uses named static factory methods ({@link #allowed} / {@link #blocked}) instead of a
 *     plain constructor for clearer call sites.
 *
 *     The decision carries:
 *       - {@code allowed}:           whether the request should be permitted to proceed
 *       - {@code remainingRequests}: how many more requests the client can make in this window
 *       - {@code retryAfter}:        how long to wait before retrying (zero when allowed)
 *
 *     These fields are used by {@link RateLimitFilter} to set the response headers:
 *       {@code X-Rate-Limit-Remaining} and {@code Retry-After}.
 *
 * ES: Objeto de valor inmutable que representa el resultado de una verificación de límite de tasa
 *     para una sola solicitud. Usa métodos de fábrica estáticos con nombre ({@link #allowed} / {@link #blocked})
 *     en lugar de un constructor simple para tener sitios de llamada más claros.
 *
 *     La decisión lleva:
 *       - {@code allowed}:           si la solicitud debe proceder
 *       - {@code remainingRequests}: cuántas solicitudes más puede hacer el cliente en esta ventana
 *       - {@code retryAfter}:        cuánto tiempo esperar antes de reintentar (cero cuando es permitido)
 *
 * Design — SOLID:
 *   SRP : Carries only the rate-limit decision data; no logic or side effects.
 */
public record RateLimitDecision(
        // EN: true if the request should be forwarded to the controller; false if it must be rejected.
        // ES: true si la solicitud debe enviarse al controlador; false si debe rechazarse.
        boolean allowed,

        // EN: Number of remaining requests in the current window (0 when blocked).
        //     Exposed as the X-Rate-Limit-Remaining response header.
        // ES: Número de solicitudes restantes en la ventana actual (0 cuando está bloqueado).
        //     Expuesto como el encabezado de respuesta X-Rate-Limit-Remaining.
        int remainingRequests,

        // EN: Duration the client should wait before retrying.
        //     Zero when the request was allowed; positive when blocked.
        //     Exposed as the Retry-After response header (in seconds).
        // ES: Duración que el cliente debe esperar antes de reintentar.
        //     Cero cuando la solicitud fue permitida; positivo cuando está bloqueada.
        //     Expuesto como el encabezado de respuesta Retry-After (en segundos).
        Duration retryAfter
) {

    /**
     * EN: Creates a "request allowed" decision with the given remaining-requests count.
     *     {@code retryAfter} is set to {@link Duration#ZERO} because there is nothing to wait for.
     *
     * ES: Crea una decisión de "solicitud permitida" con el conteo de solicitudes restantes dado.
     *     {@code retryAfter} se establece a {@link Duration#ZERO} porque no hay nada que esperar.
     *
     * @param remainingRequests number of requests still available in this window
     *                          / número de solicitudes aún disponibles en esta ventana
     * @param retryAfter        should be Duration.ZERO / debe ser Duration.ZERO
     * @return an allowed RateLimitDecision / una RateLimitDecision permitida
     */
    public static RateLimitDecision allowed(int remainingRequests, Duration retryAfter) {
        return new RateLimitDecision(true, remainingRequests, retryAfter);
    }

    /**
     * EN: Creates a "request blocked" decision with the given retry-after duration.
     *     {@code remainingRequests} is set to 0 because the window is exhausted.
     *
     * ES: Crea una decisión de "solicitud bloqueada" con la duración de reintento dada.
     *     {@code remainingRequests} se establece a 0 porque la ventana está agotada.
     *
     * @param retryAfter how long until the window resets / cuánto tiempo hasta que la ventana se reinicie
     * @return a blocked RateLimitDecision / una RateLimitDecision bloqueada
     */
    public static RateLimitDecision blocked(Duration retryAfter) {
        return new RateLimitDecision(false, 0, retryAfter);
    }
}
