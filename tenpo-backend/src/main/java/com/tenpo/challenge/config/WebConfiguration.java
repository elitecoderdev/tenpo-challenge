package com.tenpo.challenge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * EN: Spring MVC configuration that registers CORS (Cross-Origin Resource Sharing) mappings
 *     for the {@code /api/**} path group.
 *     This enables the browser to make cross-origin requests from the React frontend
 *     (served on a different port or domain) to the Spring Boot API.
 *
 *     If {@code allowedOrigins} is empty (misconfiguration or intentional lock-down),
 *     no CORS mappings are registered — meaning all cross-origin requests will be blocked
 *     by the browser. This is a safe default.
 *
 *     Allowed HTTP methods: GET, POST, PUT, DELETE, OPTIONS.
 *     All headers are permitted ({@code allowedHeaders("*")}) so that custom headers
 *     such as {@code Content-Type} and {@code X-Forwarded-For} pass without explicit listing.
 *
 * ES: Configuración de Spring MVC que registra los mapeos CORS (Cross-Origin Resource Sharing)
 *     para el grupo de rutas {@code /api/**}.
 *     Esto permite al navegador hacer solicitudes de origen cruzado desde el frontend React
 *     (servido en un puerto o dominio diferente) a la API de Spring Boot.
 *
 *     Si {@code allowedOrigins} está vacío (mala configuración o bloqueo intencional),
 *     no se registran mapeos CORS — lo que significa que todas las solicitudes de origen cruzado
 *     serán bloqueadas por el navegador. Este es un comportamiento por defecto seguro.
 *
 * Design — SOLID:
 *   SRP : Handles only CORS configuration; no routing or business logic.
 *   OCP : New allowed origins are added via configuration, not by modifying this class.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    // EN: Injected properties record that holds the list of allowed origins from application.yml.
    // ES: Registro de propiedades inyectadas que contiene la lista de orígenes permitidos de application.yml.
    private final CorsProperties corsProperties;

    public WebConfiguration(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    /**
     * EN: Registers CORS rules for the /api/** path if allowed origins are configured.
     *     If the allowed-origins list is empty, this method is a no-op — no CORS headers
     *     are sent, which effectively blocks all cross-origin requests (safe fail-closed behavior).
     *
     * ES: Registra reglas CORS para la ruta /api/** si hay orígenes permitidos configurados.
     *     Si la lista de orígenes permitidos está vacía, este método no hace nada — no se envían
     *     encabezados CORS, lo que efectivamente bloquea todas las solicitudes de origen cruzado
     *     (comportamiento seguro de fallo cerrado).
     *
     * @param registry the Spring MVC CORS registry / el registro CORS de Spring MVC
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // EN: Guard: do not register any CORS rules if the allowed origins list is empty.
        //     This prevents an accidental wildcard (*) situation.
        // ES: Guarda: no registramos reglas CORS si la lista de orígenes permitidos está vacía.
        //     Esto previene una situación de comodín (*) accidental.
        if (CollectionUtils.isEmpty(corsProperties.allowedOrigins())) {
            return;
        }

        // EN: Apply CORS to all /api/** endpoints.
        //     Convert the list to an array because the registry API requires a varargs array.
        // ES: Aplicamos CORS a todos los endpoints /api/**.
        //     Convertimos la lista a un arreglo porque la API del registro requiere un arreglo varargs.
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                // EN: Permit the full set of REST verbs plus OPTIONS for CORS preflight requests.
                // ES: Permitimos el conjunto completo de verbos REST más OPTIONS para solicitudes preflight de CORS.
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                // EN: Allow any request header; avoids the need to enumerate Content-Type, Authorization, etc.
                // ES: Permitimos cualquier encabezado de solicitud; evita la necesidad de enumerar Content-Type, Authorization, etc.
                .allowedHeaders("*");
    }
}
