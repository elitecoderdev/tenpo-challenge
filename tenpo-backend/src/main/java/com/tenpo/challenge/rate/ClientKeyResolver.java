package com.tenpo.challenge.rate;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * EN: Resolves a string key that uniquely identifies the calling client for rate-limiting purposes.
 *     The resolution order is:
 *       1. First IP in the {@code X-Forwarded-For} header (set by load balancers / reverse proxies)
 *       2. The remote IP address from the socket connection
 *       3. The fallback string {@code "anonymous-client"} if neither is available
 *
 *     Security note:
 *       {@code X-Forwarded-For} CAN be spoofed by clients. For this single-node challenge,
 *       trusting the header is an acceptable trade-off because the goal is demonstrating the
 *       pattern, not hardening it for production. In a production system behind a trusted
 *       proxy (e.g. AWS ALB), only the rightmost IP set by the proxy should be trusted.
 *
 * ES: Resuelve una clave de cadena que identifica de forma única al cliente que llama para
 *     propósitos de limitación de tasa. El orden de resolución es:
 *       1. Primera IP en el encabezado {@code X-Forwarded-For} (establecido por balanceadores de carga / proxies reversos)
 *       2. La dirección IP remota de la conexión socket
 *       3. La cadena de fallback {@code "anonymous-client"} si ninguna está disponible
 *
 *     Nota de seguridad:
 *       {@code X-Forwarded-For} PUEDE ser falsificado por clientes. Para este challenge de
 *       nodo único, confiar en el encabezado es un trade-off aceptable porque el objetivo es
 *       demostrar el patrón, no endurecerlo para producción. En un sistema de producción detrás
 *       de un proxy confiable (ej. AWS ALB), solo se debe confiar en la IP más a la derecha
 *       establecida por el proxy.
 *
 * Design — SOLID:
 *   SRP : Only resolves the client key; does not enforce limits or set headers.
 *   OCP : The resolution strategy can be changed or extended without touching RateLimitFilter.
 */
@Component
public class ClientKeyResolver {

    /**
     * EN: Derives a rate-limit key for the given HTTP request.
     *
     * ES: Deriva una clave de límite de tasa para la solicitud HTTP dada.
     *
     * @param request the incoming HTTP request / la solicitud HTTP entrante
     * @return a non-null string key identifying the client / una clave de cadena no nula que identifica al cliente
     */
    public String resolve(HttpServletRequest request) {
        // EN: Prefer X-Forwarded-For when present — allows rate limiting by real client IP
        //     even when the app sits behind a reverse proxy.
        //     Split on comma and take the first entry, which is the original client IP.
        // ES: Preferimos X-Forwarded-For cuando está presente — permite limitar por IP real del cliente
        //     incluso cuando la app está detrás de un proxy reverso.
        //     Dividimos por coma y tomamos la primera entrada, que es la IP del cliente original.
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }

        // EN: Fall back to the direct socket remote address when no proxy header is present.
        // ES: Regresamos a la dirección remota del socket directo cuando no hay encabezado de proxy.
        String remoteAddress = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddress) ? remoteAddress : "anonymous-client";
    }
}
