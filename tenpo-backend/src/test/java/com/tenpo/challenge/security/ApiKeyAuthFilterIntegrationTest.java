package com.tenpo.challenge.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * EN: Integration test for the API key authentication filter pipeline.
 *     Uses {@code @SpringBootTest} (full application context) with the "test" profile
 *     to wire the real filter chain: ApiKeyAuthFilter → RateLimitFilter → Controller.
 *
 *     The API key is overridden to a known test value via inline Spring properties
 *     so the test does not depend on the application.yml default or any .env file.
 *
 *     Scenarios covered:
 *       1. Missing X-API-Key header → 401 Unauthorized.
 *       2. Wrong X-API-Key value   → 401 Unauthorized.
 *       3. Correct X-API-Key value → request reaches the controller (200 OK).
 *       4. Security response headers are present on 401 responses.
 *       5. Swagger UI path is accessible without an API key.
 *
 * ES: Prueba de integración para el pipeline del filtro de autenticación de API key.
 *     Usa {@code @SpringBootTest} (contexto completo de aplicación) con el perfil "test"
 *     para conectar la cadena de filtros real: ApiKeyAuthFilter → RateLimitFilter → Controller.
 *
 *     La API key se sobreescribe a un valor de prueba conocido via propiedades Spring en línea
 *     para que la prueba no dependa del valor por defecto de application.yml ni de ningún archivo .env.
 *
 *     Escenarios cubiertos:
 *       1. Encabezado X-API-Key faltante → 401 Unauthorized.
 *       2. Valor X-API-Key incorrecto    → 401 Unauthorized.
 *       3. Valor X-API-Key correcto      → solicitud llega al controlador (200 OK).
 *       4. Los encabezados de respuesta de seguridad están presentes en las respuestas 401.
 *       5. La ruta de Swagger UI es accesible sin API key.
 */
@SpringBootTest(properties = {
        // EN: Pin the API key to a known test value so tests are deterministic.
        // ES: Fijamos la API key a un valor de prueba conocido para que las pruebas sean deterministas.
        "app.security.api-key=test-secret-key",
        // EN: Raise the rate-limit cap so authentication tests are not affected by quota.
        // ES: Elevamos el límite de tasa para que las pruebas de autenticación no sean afectadas por la cuota.
        "app.rate-limit.capacity=100"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiKeyAuthFilterIntegrationTest {

    // EN: The API key value must match the 'app.security.api-key' property above.
    // ES: El valor de la API key debe coincidir con la propiedad 'app.security.api-key' de arriba.
    private static final String VALID_API_KEY = "test-secret-key";

    // EN: MockMvc is auto-configured with the full filter chain including ApiKeyAuthFilter.
    // ES: MockMvc es auto-configurado con la cadena de filtros completa incluyendo ApiKeyAuthFilter.
    @Autowired
    private MockMvc mockMvc;

    // ── Missing Key Tests ─────────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that a request without the X-API-Key header is rejected with HTTP 401.
     *     The response body must be a valid ApiError JSON with status 401.
     *
     * ES: Verifica que una solicitud sin el encabezado X-API-Key sea rechazada con HTTP 401.
     *     El cuerpo de la respuesta debe ser un JSON ApiError válido con estado 401.
     */
    @Test
    void shouldRejectRequestWithNoApiKey() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ── Wrong Key Tests ───────────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that a request with an incorrect X-API-Key value is rejected with HTTP 401.
     *     This test ensures the filter validates the key content, not just its presence.
     *
     * ES: Verifica que una solicitud con un valor incorrecto de X-API-Key sea rechazada con HTTP 401.
     *     Esta prueba asegura que el filtro valide el contenido de la clave, no solo su presencia.
     */
    @Test
    void shouldRejectRequestWithWrongApiKey() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, "wrong-key-value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ── Valid Key Tests ───────────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that a request with the correct X-API-Key header passes authentication
     *     and reaches the controller, returning HTTP 200 with the transaction list.
     *
     * ES: Verifica que una solicitud con el encabezado X-API-Key correcto pase la autenticación
     *     y llegue al controlador, devolviendo HTTP 200 con la lista de transacciones.
     */
    @Test
    void shouldAllowRequestWithValidApiKey() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_API_KEY))
                .andExpect(status().isOk());
    }

    // ── Security Headers Tests ────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that security headers are present even on 401 responses.
     *     Headers must not depend on authentication success — they protect the client
     *     regardless of whether the request was authenticated.
     *
     * ES: Verifica que los encabezados de seguridad estén presentes incluso en las respuestas 401.
     *     Los encabezados no deben depender del éxito de la autenticación — protegen al cliente
     *     independientemente de si la solicitud fue autenticada.
     */
    @Test
    void shouldIncludeSecurityHeadersOnUnauthorizedResponse() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000; includeSubDomains"))
                .andExpect(header().string("X-Permitted-Cross-Domain-Policies", "none"));
    }

    /**
     * EN: Verifies that all six security headers are present on successful (200) responses.
     *     Headers must be present regardless of authentication outcome.
     *
     * ES: Verifica que los seis encabezados de seguridad estén presentes en las respuestas
     *     exitosas (200). Los encabezados deben estar presentes independientemente del resultado
     *     de la autenticación.
     */
    @Test
    void shouldIncludeSecurityHeadersOnSuccessfulResponse() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .header(ApiKeyAuthFilter.API_KEY_HEADER, VALID_API_KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000; includeSubDomains"))
                .andExpect(header().string("X-Permitted-Cross-Domain-Policies", "none"));
    }

    // ── Bypass Tests ──────────────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that the Swagger UI path is accessible without an API key.
     *     The API documentation must remain publicly reachable for integration and testing.
     *     The filter's shouldNotFilter() implementation is what allows this.
     *
     * ES: Verifica que la ruta de Swagger UI sea accesible sin una API key.
     *     La documentación de la API debe permanecer públicamente accesible para integración y pruebas.
     *     La implementación de shouldNotFilter() del filtro es lo que permite esto.
     */
    @Test
    void shouldAllowSwaggerUiWithoutApiKey() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                // EN: Swagger UI may redirect (302) or return 200; either way it must not 401.
                // ES: Swagger UI puede redirigir (302) o devolver 200; de cualquier forma no debe dar 401.
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
                });
    }
}
