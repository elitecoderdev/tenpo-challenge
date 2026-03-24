package com.tenpo.challenge.rate;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * EN: In-process, fixed-window rate limiter that tracks request counts per client key.
 *     Each unique client key (resolved by {@link ClientKeyResolver}) gets its own
 *     {@link FixedWindowCounter} stored in a Caffeine cache.
 *
 *     Algorithm — Fixed Window:
 *       A window starts when the first request for a key arrives.
 *       All requests within the window duration are counted.
 *       When the count reaches the configured capacity, subsequent requests are blocked
 *       until the window expires.
 *       A new window starts automatically after expiry.
 *
 *     Trade-offs and limitations:
 *       + Simple, no external dependencies, suitable for a single-node deployment.
 *       - Does not protect against burst traffic at window boundaries (sliding-log would).
 *       - State is in-memory only; a restarted instance or a second node resets all counters.
 *       For multi-node production use, replace with Redis + sliding-window or token-bucket.
 *
 *     The Caffeine cache is bounded to 10,000 keys and entries expire after two window
 *     durations of inactivity, preventing unbounded memory growth under many unique clients.
 *
 * ES: Limitador de tasa de ventana fija en proceso que rastrea conteos de solicitudes por clave de cliente.
 *     Cada clave de cliente única (resuelta por {@link ClientKeyResolver}) obtiene su propio
 *     {@link FixedWindowCounter} almacenado en un cache Caffeine.
 *
 *     Algoritmo — Ventana Fija:
 *       Una ventana comienza cuando llega la primera solicitud para una clave.
 *       Todas las solicitudes dentro de la duración de la ventana se cuentan.
 *       Cuando el conteo alcanza la capacidad configurada, las solicitudes posteriores se bloquean
 *       hasta que la ventana expire. Una nueva ventana comienza automáticamente después de expirar.
 *
 *     Trade-offs y limitaciones:
 *       + Simple, sin dependencias externas, adecuado para despliegue de nodo único.
 *       - No protege contra tráfico en ráfaga en los límites de ventana (ventana deslizante sería mejor).
 *       - El estado es solo en memoria; una instancia reiniciada o un segundo nodo reinicia todos los contadores.
 *       Para uso de producción multinodo, reemplazar con Redis + ventana deslizante o token-bucket.
 *
 *     El cache Caffeine está limitado a 10,000 claves y las entradas expiran después de dos duraciones
 *     de ventana de inactividad, previniendo crecimiento de memoria no acotado con muchos clientes únicos.
 *
 * Design — SOLID:
 *   SRP : Owns only the rate-limit state machine; does not handle HTTP or configuration parsing.
 *   OCP : The inner FixedWindowCounter can be swapped for a different algorithm (e.g. sliding log)
 *         by changing only this class — RateLimitFilter remains untouched.
 */
@Component
public class ClientRateLimiter {

    // EN: Caffeine loading cache keyed by client identifier.
    //     Each value is a FixedWindowCounter that tracks the request count for that client.
    //     LoadingCache auto-creates a new counter on the first access for a previously unseen key.
    // ES: Cache de carga Caffeine indexado por identificador de cliente.
    //     Cada valor es un FixedWindowCounter que rastrea el conteo de solicitudes para ese cliente.
    //     LoadingCache crea automáticamente un nuevo contador al primer acceso para una clave no vista antes.
    private final LoadingCache<String, FixedWindowCounter> windows;

    // EN: Configuration properties loaded from application.yml / environment variables.
    // ES: Propiedades de configuración cargadas desde application.yml / variables de entorno.
    private final RateLimitProperties rateLimitProperties;

    /**
     * EN: Builds the Caffeine cache when the bean is constructed.
     *     The cache is bounded (maximumSize) to prevent unbounded growth.
     *     Entries expire after 2× the window duration of inactivity so that idle clients
     *     do not consume memory indefinitely.
     *
     * ES: Construye el cache Caffeine cuando se construye el bean.
     *     El cache está acotado (maximumSize) para prevenir crecimiento no acotado.
     *     Las entradas expiran después de 2× la duración de la ventana de inactividad para que
     *     los clientes inactivos no consuman memoria indefinidamente.
     */
    public ClientRateLimiter(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
        this.windows = Caffeine.newBuilder()
                .maximumSize(10_000)
                // EN: Expire entries after 2× the window duration of inactivity to reclaim memory.
                // ES: Expiramos entradas después de 2× la duración de la ventana de inactividad para recuperar memoria.
                .expireAfterAccess(rateLimitProperties.duration().multipliedBy(2))
                .build(key -> new FixedWindowCounter());
    }

    /**
     * EN: Attempts to consume one token for the given client key.
     *     Returns an {@link RateLimitDecision} describing whether the request is allowed
     *     and, if blocked, how long the client should wait before retrying.
     *
     * ES: Intenta consumir un token para la clave de cliente dada.
     *     Devuelve un {@link RateLimitDecision} describiendo si la solicitud está permitida
     *     y, si está bloqueada, cuánto tiempo debe esperar el cliente antes de reintentar.
     *
     * @param clientKey the resolved client identifier / el identificador de cliente resuelto
     * @return the rate-limit decision for this request / la decisión de límite de tasa para esta solicitud
     */
    public RateLimitDecision tryConsume(String clientKey) {
        // EN: windows.get() is thread-safe: Caffeine guarantees at-most-once creation per key.
        //     consume() on the counter is synchronized to prevent data races within the same key.
        // ES: windows.get() es thread-safe: Caffeine garantiza creación como máximo una vez por clave.
        //     consume() en el contador está sincronizado para prevenir condiciones de carrera dentro de la misma clave.
        return windows.get(clientKey)
                .consume(Instant.now(), rateLimitProperties.capacity(), rateLimitProperties.duration());
    }

    // ── Inner Class: Fixed Window Counter ─────────────────────────────────────────────────

    /**
     * EN: Thread-safe fixed-window counter for a single client key.
     *     State is isolated per key so concurrent requests from different clients never contend.
     *     Synchronization is on the counter instance (not on ClientRateLimiter) so the lock
     *     scope is minimal.
     *
     * ES: Contador de ventana fija thread-safe para una sola clave de cliente.
     *     El estado está aislado por clave para que las solicitudes concurrentes de diferentes
     *     clientes nunca compitan. La sincronización es en la instancia del contador
     *     (no en ClientRateLimiter) para que el alcance del lock sea mínimo.
     */
    private static final class FixedWindowCounter {

        // EN: The moment when the current window started. Null before the first request.
        // ES: El momento en que comenzó la ventana actual. Null antes de la primera solicitud.
        private Instant windowStart;

        // EN: Number of requests consumed so far in the current window.
        // ES: Número de solicitudes consumidas hasta ahora en la ventana actual.
        private int consumedTokens;

        // EN: A synchronized fixed window is enough for this single-node challenge.
        // ES: Una ventana fija sincronizada es suficiente para este desafio de una sola instancia.
        /**
         * EN: Attempts to consume one token. If the current window has expired, a new window
         *     is opened. Returns an allowed or blocked decision based on the remaining capacity.
         *
         * ES: Intenta consumir un token. Si la ventana actual ha expirado, se abre una nueva ventana.
         *     Devuelve una decisión de permitido o bloqueado basada en la capacidad restante.
         *
         * @param now      the current instant / el instante actual
         * @param capacity max requests per window / máximas solicitudes por ventana
         * @param duration window duration / duración de la ventana
         * @return the rate-limit decision / la decisión de límite de tasa
         */
        synchronized RateLimitDecision consume(Instant now, int capacity, Duration duration) {
            // EN: Reset the window if this is the first request ever, or if the current window has expired.
            // ES: Reiniciamos la ventana si esta es la primera solicitud o si la ventana actual ha expirado.
            if (Objects.isNull(windowStart) || !now.isBefore(windowStart.plus(duration))) {
                windowStart = now;
                consumedTokens = 0;
            }

            // EN: Allow the request if the client has remaining capacity in the current window.
            // ES: Permitimos la solicitud si el cliente tiene capacidad restante en la ventana actual.
            if (consumedTokens < capacity) {
                consumedTokens++;
                return RateLimitDecision.allowed(capacity - consumedTokens, Duration.ZERO);
            }

            // EN: Block the request and calculate how long until the window resets.
            //     Use ceiling division (+ 999) to round up to the next full second so
            //     the Retry-After header is never shorter than the actual remaining time.
            // ES: Bloqueamos la solicitud y calculamos cuánto tiempo hasta que la ventana se reinicie.
            //     Usamos división de techo (+ 999) para redondear al próximo segundo completo para que
            //     el encabezado Retry-After nunca sea más corto que el tiempo restante real.
            Duration retryAfter = Duration.between(now, windowStart.plus(duration));
            long retryAfterSeconds = Math.max(1, (retryAfter.toMillis() + 999) / 1_000);
            return RateLimitDecision.blocked(Duration.ofSeconds(retryAfterSeconds));
        }
    }
}
