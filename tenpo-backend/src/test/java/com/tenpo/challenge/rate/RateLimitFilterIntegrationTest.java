package com.tenpo.challenge.rate;

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
 * EN: Integration test for the rate-limit filter pipeline.
 *     Uses {@code @SpringBootTest} (full application context) with the "test" profile
 *     to wire the real filter chain: ClientKeyResolver → ClientRateLimiter → RateLimitFilter.
 *     The rate limit is set to capacity=3 via inline Spring properties so the test does not
 *     depend on the default application.yml value.
 *
 *     This test exercises the full HTTP path from request to response without a live database:
 *       - Requests 1-3 must be allowed (HTTP 200) with X-Rate-Limit-Limit = 3.
 *       - Request 4 must be blocked (HTTP 429) with Retry-After and the ApiError JSON body.
 *
 *     The X-Forwarded-For header is set to a fixed IP (10.0.0.1) so the test client is always
 *     identified the same way regardless of the test runner's network configuration.
 *
 * ES: Prueba de integración para el pipeline del filtro de límite de tasa.
 *     Usa {@code @SpringBootTest} (contexto completo de aplicación) con el perfil "test"
 *     para conectar la cadena de filtros real: ClientKeyResolver → ClientRateLimiter → RateLimitFilter.
 *     El límite de tasa se establece en capacity=3 via propiedades Spring en línea para que la prueba
 *     no dependa del valor predeterminado de application.yml.
 *
 *     Esta prueba ejerce la ruta HTTP completa desde la solicitud hasta la respuesta sin base de datos real:
 *       - Solicitudes 1-3 deben ser permitidas (HTTP 200) con X-Rate-Limit-Limit = 3.
 *       - Solicitud 4 debe ser bloqueada (HTTP 429) con Retry-After y el cuerpo JSON ApiError.
 */
@SpringBootTest(properties = {
        // EN: Override the capacity so this test does not depend on the application.yml default.
        //     3 is the challenge-specified limit.
        // ES: Sobreescribimos la capacidad para que esta prueba no dependa del valor predeterminado de application.yml.
        //     3 es el límite especificado por el challenge.
        "app.rate-limit.capacity=3",
        "app.rate-limit.duration=PT1M"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitFilterIntegrationTest {

    // EN: MockMvc is auto-configured with the full filter chain including RateLimitFilter.
    // ES: MockMvc es auto-configurado con la cadena de filtros completa incluyendo RateLimitFilter.
    @Autowired
    private MockMvc mockMvc;

    /**
     * EN: Verifies the full rate-limit enforcement cycle for a single client:
     *       - The first 3 requests must succeed (200 OK).
     *       - The 4th request must be rejected (429 Too Many Requests).
     *     The X-Rate-Limit-Limit header must always equal the configured capacity (3).
     *     The 429 response must include Retry-After and a structured ApiError body with status 429.
     *
     * ES: Verifica el ciclo completo de cumplimiento del límite de tasa para un solo cliente:
     *       - Las primeras 3 solicitudes deben tener éxito (200 OK).
     *       - La 4ª solicitud debe ser rechazada (429 Too Many Requests).
     *     El encabezado X-Rate-Limit-Limit siempre debe ser igual a la capacidad configurada (3).
     *     La respuesta 429 debe incluir Retry-After y un cuerpo ApiError estructurado con estado 429.
     */
    @Test
    void shouldBlockFourthRequestWithinTheSameMinute() throws Exception {
        // EN: Requests 1, 2, and 3 — all within the allowed window. Each must return 200.
        // ES: Solicitudes 1, 2 y 3 — todas dentro de la ventana permitida. Cada una debe devolver 200.
        for (int requestNumber = 0; requestNumber < 3; requestNumber++) {
            mockMvc.perform(get("/api/transactions").header("X-Forwarded-For", "10.0.0.1"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Rate-Limit-Limit", "3"));
        }

        // EN: Request 4 — window is exhausted; must be rejected with 429.
        //     Retry-After should be 60 seconds (the window duration in seconds).
        //     The response body must be a valid ApiError JSON with status 429.
        // ES: Solicitud 4 — la ventana está agotada; debe ser rechazada con 429.
        //     Retry-After debe ser 60 segundos (la duración de la ventana en segundos).
        //     El cuerpo de la respuesta debe ser un JSON ApiError válido con estado 429.
        mockMvc.perform(get("/api/transactions").header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429));
    }
}
