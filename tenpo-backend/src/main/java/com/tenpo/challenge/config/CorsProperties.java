package com.tenpo.challenge.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EN: Strongly-typed configuration record that binds the {@code app.cors} property group.
 *     Reads the {@code app.cors.allowed-origins} list from {@code application.yml} and
 *     exposes it as an immutable Java list.
 *
 *     Default (from {@code application.yml}):
 *       allowed-origins:
 *         - http://localhost:5173  (Vite dev server)
 *         - http://localhost:4173  (Vite preview)
 *         - http://localhost:3000  (Docker nginx)
 *         - http://127.0.0.1:*    (localhost equivalents)
 *
 *     Override at runtime via the environment variable:
 *       APP_CORS_ALLOWED_ORIGINS=https://yourdomain.com,https://other.com
 *
 * ES: Registro de configuración fuertemente tipado que vincula el grupo de propiedades {@code app.cors}.
 *     Lee la lista {@code app.cors.allowed-origins} de {@code application.yml} y
 *     la expone como una lista Java inmutable.
 *
 * Design — SOLID:
 *   SRP : Only carries the CORS origins list; consumed by WebConfiguration.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        // EN: List of origin strings that browsers are permitted to use when making cross-origin
        //     requests to the API. Each entry must be the exact protocol + host + port.
        // ES: Lista de cadenas de origen que los navegadores pueden usar al hacer solicitudes
        //     de origen cruzado a la API. Cada entrada debe ser el protocolo + host + puerto exacto.
        List<String> allowedOrigins
) {
}
