package com.tenpo.challenge.rate;

import com.tenpo.challenge.shared.api.ApiError;
import com.tenpo.challenge.shared.api.ApiErrorFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * EN: Servlet filter that enforces per-client rate limiting on all {@code /api/**} endpoints.
 *     Extends {@link OncePerRequestFilter} to guarantee exactly one execution per request,
 *     regardless of any servlet forward or include chains.
 *
 *     On every request to {@code /api/**}:
 *       1. The client key is resolved by {@link ClientKeyResolver}.
 *       2. The key is checked against {@link ClientRateLimiter}.
 *       3. {@code X-Rate-Limit-Limit} and {@code X-Rate-Limit-Remaining} headers are always set.
 *       4. If blocked, the filter short-circuits with HTTP 429 and a JSON {@link ApiError} body.
 *       5. If allowed, the request is forwarded through the rest of the filter chain.
 *
 *     Security note: The filter returns a structured JSON error body (via ObjectMapper) instead
 *     of letting Spring's default error page respond. This prevents HTML error pages from being
 *     rendered for API consumers.
 *
 * ES: Filtro de servlet que hace cumplir la limitación de tasa por cliente en todos los
 *     endpoints {@code /api/**}. Extiende {@link OncePerRequestFilter} para garantizar
 *     exactamente una ejecución por solicitud, independientemente de cualquier cadena
 *     de forward o include de servlet.
 *
 *     En cada solicitud a {@code /api/**}:
 *       1. La clave del cliente es resuelta por {@link ClientKeyResolver}.
 *       2. La clave se verifica contra {@link ClientRateLimiter}.
 *       3. Los encabezados {@code X-Rate-Limit-Limit} y {@code X-Rate-Limit-Remaining} siempre se establecen.
 *       4. Si está bloqueada, el filtro hace cortocircuito con HTTP 429 y un cuerpo JSON {@link ApiError}.
 *       5. Si está permitida, la solicitud se reenvía a través del resto de la cadena de filtros.
 *
 * Design — SOLID:
 *   SRP : Handles only the HTTP rate-limit enforcement; key resolution and counting are delegated.
 *   DIP : All collaborators (limiter, resolver, factory, mapper) injected via constructor.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // EN: All collaborators are injected via constructor following Dependency Inversion.
    // ES: Todos los colaboradores se inyectan via constructor siguiendo la Inversión de Dependencias.
    private final ClientRateLimiter clientRateLimiter;
    private final ClientKeyResolver clientKeyResolver;
    private final ApiErrorFactory apiErrorFactory;
    private final ObjectMapper objectMapper;
    private final RateLimitProperties rateLimitProperties;

    public RateLimitFilter(
            ClientRateLimiter clientRateLimiter,
            ClientKeyResolver clientKeyResolver,
            ApiErrorFactory apiErrorFactory,
            ObjectMapper objectMapper,
            RateLimitProperties rateLimitProperties
    ) {
        this.clientRateLimiter = clientRateLimiter;
        this.clientKeyResolver = clientKeyResolver;
        this.apiErrorFactory = apiErrorFactory;
        this.objectMapper = objectMapper;
        this.rateLimitProperties = rateLimitProperties;
    }

    /**
     * EN: Exempts non-API paths (e.g. Swagger, health checks) from rate limiting.
     *     Returning {@code true} causes {@link OncePerRequestFilter} to skip
     *     {@link #doFilterInternal} entirely for that request.
     *
     * ES: Exime las rutas no API (ej. Swagger, health checks) de la limitación de tasa.
     *     Devolver {@code true} hace que {@link OncePerRequestFilter} omita
     *     {@link #doFilterInternal} completamente para esa solicitud.
     *
     * @param request the incoming request / la solicitud entrante
     * @return true if rate limiting should be skipped / true si se debe omitir la limitación de tasa
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // EN: Only apply rate limiting to paths that start with /api/.
        //     This excludes /swagger-ui, /v3/api-docs, /actuator/health, etc.
        // ES: Solo aplicamos la limitación de tasa a rutas que comienzan con /api/.
        //     Esto excluye /swagger-ui, /v3/api-docs, /actuator/health, etc.
        return !request.getRequestURI().startsWith("/api/");
    }

    /**
     * EN: Core filter logic. Called exactly once per request for {@code /api/**} paths.
     *     Resolves the client, checks the rate limit, sets informational headers, and either
     *     forwards the request or rejects it with a 429 response.
     *
     * ES: Lógica central del filtro. Llamado exactamente una vez por solicitud para rutas {@code /api/**}.
     *     Resuelve el cliente, verifica el límite de tasa, establece encabezados informativos y
     *     reenvía la solicitud o la rechaza con una respuesta 429.
     *
     * @param request     the incoming HTTP request / la solicitud HTTP entrante
     * @param response    the HTTP response / la respuesta HTTP
     * @param filterChain the remaining filter chain / la cadena de filtros restante
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        // EN: Step 1 — Identify the client by IP (or X-Forwarded-For header).
        // ES: Paso 1 — Identificamos al cliente por IP (o encabezado X-Forwarded-For).
        String clientKey = clientKeyResolver.resolve(request);

        // EN: Step 2 — Attempt to consume one request token from the client's window.
        // ES: Paso 2 — Intentamos consumir un token de solicitud de la ventana del cliente.
        RateLimitDecision decision = clientRateLimiter.tryConsume(clientKey);

        // EN: Step 3 — Always set the rate-limit informational headers so clients can
        //     track their own quota consumption without waiting for a 429 response.
        // ES: Paso 3 — Siempre establecemos los encabezados informativos de límite de tasa para que
        //     los clientes puedan rastrear su propio consumo de cuota sin esperar una respuesta 429.
        response.setHeader("X-Rate-Limit-Limit", String.valueOf(rateLimitProperties.capacity()));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(decision.remainingRequests()));

        // EN: Step 4 — Short-circuit with 429 if the client has exhausted their window.
        //     Write a structured JSON error body instead of relying on Spring's default error page.
        // ES: Paso 4 — Hacemos cortocircuito con 429 si el cliente ha agotado su ventana.
        //     Escribimos un cuerpo de error JSON estructurado en lugar de depender de la página de error por defecto de Spring.
        if (!decision.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

            // EN: Retry-After header (RFC 6585) tells the client how many seconds to wait.
            // ES: El encabezado Retry-After (RFC 6585) le dice al cliente cuántos segundos esperar.
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfter().toSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            // EN: Build and serialize a structured ApiError into the response body.
            //     This keeps the 429 response consistent with all other error responses from the API.
            // ES: Construimos y serializamos un ApiError estructurado en el cuerpo de la respuesta.
            //     Esto mantiene la respuesta 429 consistente con todas las demás respuestas de error de la API.
            ApiError apiError = apiErrorFactory.build(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Only 3 requests per minute are allowed per client.",
                    request.getRequestURI(),
                    List.of()
            );

            objectMapper.writeValue(response.getWriter(), apiError);
            return;
        }

        // EN: Step 5 — Request is within quota; pass it along the filter chain to the controller.
        // ES: Paso 5 — La solicitud está dentro de la cuota; la pasamos por la cadena de filtros al controlador.
        filterChain.doFilter(request, response);
    }
}
