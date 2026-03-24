package com.tenpo.challenge.rate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * EN: Strongly-typed, externalized configuration for the rate-limiter component.
 *     Bound to the {@code app.rate-limit} prefix in {@code application.yml}.
 *     {@code @Validated} ensures that the properties satisfy their constraints
 *     at application startup — failing fast rather than silently misconfiguring.
 *
 *     Default values (from {@code application.yml}):
 *       capacity = 3   (overridable via APP_RATE_LIMIT_CAPACITY environment variable)
 *       duration = PT1M (1 minute, overridable via APP_RATE_LIMIT_DURATION)
 *
 *     Using a {@code record} here provides immutability: once the properties are bound
 *     at startup they cannot be mutated, preventing accidental runtime changes.
 *
 * ES: Configuración externalizada y fuertemente tipada para el componente de limitador de tasa.
 *     Vinculada al prefijo {@code app.rate-limit} en {@code application.yml}.
 *     {@code @Validated} asegura que las propiedades satisfagan sus restricciones
 *     al inicio de la aplicación — fallando rápido en lugar de configurar incorrectamente en silencio.
 *
 *     Valores por defecto (desde {@code application.yml}):
 *       capacity = 3   (sobreescribible via variable de entorno APP_RATE_LIMIT_CAPACITY)
 *       duration = PT1M (1 minuto, sobreescribible via APP_RATE_LIMIT_DURATION)
 *
 * Design — SOLID:
 *   SRP : Carries only configuration values; no rate-limit logic.
 *   OCP : New rate-limit parameters can be added here without touching the limiter.
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(

        // EN: Maximum number of requests allowed per client within a single window.
        //     @Positive ensures this is at least 1 — a zero capacity would block all traffic.
        // ES: Número máximo de solicitudes permitidas por cliente dentro de una sola ventana.
        //     @Positive asegura que sea al menos 1 — una capacidad cero bloquearía todo el tráfico.
        @Positive int capacity,

        // EN: The duration of each fixed window (e.g. PT1M = 1 minute).
        //     Spring Boot automatically converts ISO-8601 duration strings from the YAML value.
        //     @NotNull prevents misconfiguration via a missing or null environment variable.
        // ES: La duración de cada ventana fija (ej. PT1M = 1 minuto).
        //     Spring Boot convierte automáticamente cadenas de duración ISO-8601 desde el valor YAML.
        //     @NotNull previene mala configuración via variable de entorno ausente o nula.
        @NotNull Duration duration
) {
}
