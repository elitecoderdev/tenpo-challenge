package com.tenpo.challenge.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenpo.challenge.shared.api.ApiError;
import com.tenpo.challenge.shared.api.ApiErrorFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * EN: Servlet filter that authenticates every request to {@code /api/**} endpoints
 *     by validating the {@code X-API-Key} request header against the configured secret.
 *
 *     Runs at {@code @Order(1)} — before the rate-limit filter — so that unauthenticated
 *     requests are rejected immediately without consuming quota.
 *
 *     On every request to {@code /api/**}:
 *       1. Security headers are added to the response (always, even for 401s).
 *       2. The {@code X-API-Key} header is read from the request.
 *       3. If missing or wrong: short-circuit with HTTP 401 + structured {@link ApiError} body.
 *       4. If correct: forward to the next filter in the chain (rate limit → controller).
 *
 *     Excluded paths (via {@link #shouldNotFilter}):
 *       - {@code /swagger-ui/**} — Swagger UI must be accessible for documentation/testing.
 *       - {@code /v3/api-docs/**} — OpenAPI JSON used by Swagger UI.
 *       - {@code /actuator/**}   — Health and info endpoints for monitoring.
 *
 *     Security headers added on every response:
 *       - {@code X-Content-Type-Options: nosniff}                        — prevents MIME-type sniffing.
 *       - {@code X-Frame-Options: DENY}                                   — prevents clickjacking.
 *       - {@code Cache-Control: no-store}                                 — financial data must not be cached.
 *       - {@code Content-Security-Policy: default-src 'none'}             — disallows all resources (pure API).
 *       - {@code Strict-Transport-Security: max-age=31536000; includeSubDomains} — enforces HTTPS (HSTS).
 *       - {@code X-Permitted-Cross-Domain-Policies: none}                 — blocks Adobe Flash/PDF access.
 *
 *     API key comparison uses {@link MessageDigest#isEqual} (constant-time) to prevent timing attacks.
 *
 * ES: Filtro de servlet que autentica cada solicitud a los endpoints {@code /api/**}
 *     validando el encabezado de solicitud {@code X-API-Key} contra el secreto configurado.
 *
 *     Se ejecuta en {@code @Order(1)} — antes del filtro de límite de tasa — para que las
 *     solicitudes no autenticadas sean rechazadas inmediatamente sin consumir cuota.
 *
 *     En cada solicitud a {@code /api/**}:
 *       1. Se agregan encabezados de seguridad a la respuesta (siempre, incluso para 401s).
 *       2. El encabezado {@code X-API-Key} se lee de la solicitud.
 *       3. Si falta o es incorrecto: cortocircuito con HTTP 401 + cuerpo {@link ApiError} estructurado.
 *       4. Si es correcto: se reenvía al siguiente filtro en la cadena (rate limit → controlador).
 *
 *     Rutas excluidas (via {@link #shouldNotFilter}):
 *       - {@code /swagger-ui/**} — Swagger UI debe ser accesible para documentación/pruebas.
 *       - {@code /v3/api-docs/**} — JSON OpenAPI usado por Swagger UI.
 *       - {@code /actuator/**}   — Endpoints de salud e info para monitoreo.
 *
 *     Encabezados de seguridad agregados en cada respuesta:
 *       - {@code X-Content-Type-Options: nosniff}                         — previene el sniffing de tipo MIME.
 *       - {@code X-Frame-Options: DENY}                                    — previene clickjacking.
 *       - {@code Cache-Control: no-store}                                  — datos financieros no deben cachearse.
 *       - {@code Content-Security-Policy: default-src 'none'}              — deshabilita todos los recursos (API pura).
 *       - {@code Strict-Transport-Security: max-age=31536000; includeSubDomains} — fuerza HTTPS (HSTS).
 *       - {@code X-Permitted-Cross-Domain-Policies: none}                  — bloquea acceso de Flash/PDF de Adobe.
 *
 * Design — SOLID:
 *   SRP : Only handles authentication and security headers; rate limiting is in RateLimitFilter.
 *   OCP : The key validation strategy can be changed by replacing ApiKeyProperties without
 *         modifying this filter (e.g. swap to a set of keys, or delegate to a key store).
 *   DIP : All collaborators injected via constructor; no static state.
 */
@Component
@Order(1)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    // EN: The standard header name for API key authentication.
    // ES: El nombre de encabezado estándar para autenticación con API key.
    static final String API_KEY_HEADER = "X-API-Key";

    // EN: Collaborators injected via constructor (Dependency Inversion Principle).
    // ES: Colaboradores inyectados via constructor (Principio de Inversión de Dependencias).
    private final ApiKeyProperties apiKeyProperties;
    private final ApiErrorFactory apiErrorFactory;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(
            ApiKeyProperties apiKeyProperties,
            ApiErrorFactory apiErrorFactory,
            ObjectMapper objectMapper
    ) {
        this.apiKeyProperties = apiKeyProperties;
        this.apiErrorFactory = apiErrorFactory;
        this.objectMapper = objectMapper;
    }

    /**
     * EN: Exempts documentation and monitoring paths from authentication.
     *     These paths must remain publicly accessible so that the API can be
     *     explored via Swagger UI and monitored without a key.
     *
     * ES: Exime las rutas de documentación y monitoreo de la autenticación.
     *     Estas rutas deben permanecer accesibles públicamente para que la API pueda
     *     explorarse via Swagger UI y monitorearse sin una clave.
     *
     * @param request the incoming request / la solicitud entrante
     * @return true if this filter should be skipped / true si este filtro debe omitirse
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        // EN: Bypass auth for Swagger UI, OpenAPI JSON, and Actuator health/info.
        // ES: Saltamos autenticación para Swagger UI, JSON OpenAPI y Actuator health/info.
        return uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/actuator");
    }

    /**
     * EN: Core authentication logic. Adds security headers, then validates the API key.
     *     Short-circuits with HTTP 401 if the key is missing or does not match the configured value.
     *     Forwards the request to the next filter if authentication succeeds.
     *
     * ES: Lógica central de autenticación. Agrega encabezados de seguridad, luego valida la API key.
     *     Hace cortocircuito con HTTP 401 si la clave falta o no coincide con el valor configurado.
     *     Reenvía la solicitud al siguiente filtro si la autenticación tiene éxito.
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

        // EN: Step 1 — Add security headers to every /api/** response, including 401s.
        //     These protect the client regardless of whether the request is authenticated.
        // ES: Paso 1 — Agregamos encabezados de seguridad a cada respuesta /api/**, incluyendo 401s.
        //     Estos protegen al cliente independientemente de si la solicitud está autenticada.
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Cache-Control", "no-store");
        // EN: This is a pure REST API — no scripts, stylesheets, or external resources should be loaded.
        // ES: Esta es una API REST pura — no se deben cargar scripts, hojas de estilo ni recursos externos.
        response.setHeader("Content-Security-Policy", "default-src 'none'");
        // EN: Instruct browsers to use HTTPS exclusively for the next year.
        //     Safe to include even in HTTP-only dev environments; browsers ignore it without TLS.
        // ES: Le indica a los navegadores usar HTTPS exclusivamente durante el próximo año.
        //     Seguro incluirlo incluso en entornos dev HTTP-only; los navegadores lo ignoran sin TLS.
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        // EN: Blocks Adobe Flash and PDF readers from making cross-domain requests to this API.
        // ES: Bloquea a Flash y lectores de PDF de Adobe de hacer solicitudes cross-domain a esta API.
        response.setHeader("X-Permitted-Cross-Domain-Policies", "none");

        // EN: Step 2 — Read the API key from the request header.
        // ES: Paso 2 — Leemos la API key del encabezado de la solicitud.
        String providedKey = request.getHeader(API_KEY_HEADER);

        // EN: Step 3 — Constant-time key comparison using MessageDigest.isEqual to prevent timing attacks.
        //     String.equals() short-circuits on the first differing byte, leaking key length/prefix
        //     information via response time. MessageDigest.isEqual always compares every byte,
        //     making timing observations useless to an attacker.
        //     We treat a missing key as an empty string so the comparison always runs
        //     (and always fails), keeping the timing consistent whether the key is absent or wrong.
        // ES: Comparación de clave en tiempo constante usando MessageDigest.isEqual para prevenir ataques de temporización.
        //     String.equals() cortocircuita en el primer byte diferente, filtrando información sobre
        //     la longitud/prefijo de la clave via tiempo de respuesta. MessageDigest.isEqual siempre
        //     compara todos los bytes, haciendo inútiles las observaciones de tiempo para un atacante.
        //     Tratamos una clave faltante como string vacío para que la comparación siempre se ejecute
        //     (y siempre falle), manteniendo el tiempo consistente tanto si la clave falta como si es incorrecta.
        String expectedKey = apiKeyProperties.apiKey();
        String incomingKey = (providedKey != null) ? providedKey : "";
        boolean keyValid = MessageDigest.isEqual(
                expectedKey.getBytes(StandardCharsets.UTF_8),
                incomingKey.getBytes(StandardCharsets.UTF_8)
        );

        if (!keyValid) {
            // EN: Build a human-readable message that distinguishes missing key from wrong key.
            //     This helps developers integrate without leaking key material to attackers
            //     (both cases still return 401 — the message is informational only).
            //     Note: we use `providedKey` (the original header value) for this check,
            //     not `incomingKey`, so the message correctly reflects the missing-key case.
            // ES: Construimos un mensaje legible que distingue la clave faltante de la incorrecta.
            //     Esto ayuda a los desarrolladores a integrarse sin filtrar material de clave a atacantes
            //     (ambos casos siguen devolviendo 401 — el mensaje es solo informativo).
            //     Nota: usamos `providedKey` (el valor original del encabezado) para esta verificación,
            //     no `incomingKey`, para que el mensaje refleje correctamente el caso de clave faltante.
            String message = (providedKey == null)
                    ? "Authentication required. Provide a valid API key in the X-API-Key header."
                    : "Invalid API key. Check the value provided in the X-API-Key header.";

            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            // EN: Write a structured ApiError body consistent with all other error responses.
            // ES: Escribimos un cuerpo ApiError estructurado consistente con todas las demás respuestas de error.
            ApiError apiError = apiErrorFactory.build(
                    HttpStatus.UNAUTHORIZED,
                    message,
                    request.getRequestURI(),
                    List.of()
            );
            objectMapper.writeValue(response.getWriter(), apiError);
            return;
        }

        // EN: Step 4 — API key is valid; forward to the next filter (RateLimitFilter → controller).
        // ES: Paso 4 — La API key es válida; reenviamos al siguiente filtro (RateLimitFilter → controlador).
        filterChain.doFilter(request, response);
    }
}
