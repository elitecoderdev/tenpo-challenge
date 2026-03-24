package com.tenpo.challenge.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * EN: Typed configuration properties for the API key security filter.
 *     Bound from the {@code app.security} prefix in {@code application.yml}.
 *     The key is read from the {@code APP_API_KEY} environment variable at runtime,
 *     with {@code tenpo-dev-key} as a development fallback so the app starts
 *     without manual configuration in local development.
 *
 *     Usage: set {@code APP_API_KEY=<your-secret>} in {@code .env} or the container
 *     environment before deploying to any non-development environment.
 *
 * ES: Propiedades de configuración tipadas para el filtro de seguridad de API key.
 *     Enlazadas desde el prefijo {@code app.security} en {@code application.yml}.
 *     La clave se lee desde la variable de entorno {@code APP_API_KEY} en tiempo de ejecución,
 *     con {@code tenpo-dev-key} como valor de respaldo para desarrollo, permitiendo que la app
 *     arranque sin configuración manual en desarrollo local.
 *
 *     Uso: establecer {@code APP_API_KEY=<tu-secreto>} en {@code .env} o en el entorno
 *     del contenedor antes de desplegar a cualquier entorno que no sea desarrollo.
 *
 * Design — SOLID:
 *   SRP : Only carries the security configuration value; no logic.
 *   DIP : Injected into ApiKeyAuthFilter via constructor; never read from environment directly.
 */
@Validated
@ConfigurationProperties(prefix = "app.security")
public record ApiKeyProperties(

        // EN: The expected API key value. Must not be blank — the @NotBlank constraint
        //     causes Spring Boot to fail fast at startup if the property resolves to empty.
        //     In production, always override via APP_API_KEY environment variable.
        // ES: El valor esperado de la API key. No debe estar en blanco — la restricción @NotBlank
        //     hace que Spring Boot falle rápido al arrancar si la propiedad se resuelve a vacío.
        //     En producción, siempre sobreescribir via variable de entorno APP_API_KEY.
        @NotBlank String apiKey
) {
}
