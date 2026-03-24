package com.tenpo.challenge.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EN: Spring configuration class that produces the OpenAPI metadata bean consumed by
 *     springdoc-openapi to generate the Swagger UI and the {@code /v3/api-docs} JSON.
 *
 *     After application startup the documentation is available at:
 *       Swagger UI : http://localhost:8080/swagger-ui
 *       OpenAPI JSON: http://localhost:8080/v3/api-docs
 *
 *     The {@code @Tag} and {@code @Operation} annotations on the controller and methods
 *     provide per-endpoint descriptions that appear in the generated UI.
 *
 * ES: Clase de configuración Spring que produce el bean de metadatos OpenAPI consumido por
 *     springdoc-openapi para generar la interfaz Swagger UI y el JSON {@code /v3/api-docs}.
 *
 *     Después del inicio de la aplicación, la documentación está disponible en:
 *       Swagger UI : http://localhost:8080/swagger-ui
 *       JSON OpenAPI: http://localhost:8080/v3/api-docs
 *
 * Design — SOLID:
 *   SRP : Only configures the OpenAPI documentation metadata; no routing or business logic.
 */
@Configuration
public class OpenApiConfiguration {

    /**
     * EN: Defines the API metadata that appears in the Swagger UI header:
     *     title, description, version, contact, and license.
     *
     * ES: Define los metadatos de la API que aparecen en el encabezado de Swagger UI:
     *     título, descripción, versión, contacto y licencia.
     *
     * @return the OpenAPI bean / el bean OpenAPI
     */
    /**
     * EN: The name used to reference the security scheme in @SecurityRequirement annotations
     *     on individual controllers and operations.
     *
     * ES: El nombre usado para referenciar el esquema de seguridad en las anotaciones
     *     @SecurityRequirement en controladores y operaciones individuales.
     */
    static final String API_KEY_SCHEME = "ApiKey";

    @Bean
    OpenAPI tenpoOpenApi() {
        // EN: Define the X-API-Key header security scheme so Swagger UI renders an
        //     "Authorize" button where testers can enter the key before trying endpoints.
        // ES: Definimos el esquema de seguridad de encabezado X-API-Key para que Swagger UI
        //     renderice un botón "Authorize" donde los testers pueden ingresar la clave antes
        //     de probar los endpoints.
        SecurityScheme apiKeyScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-API-Key")
                .description("API key required for all /api/** endpoints. "
                        + "In local development use the default value: tenpo-dev-key");

        return new OpenAPI()
                .info(new Info()
                        // EN: Title shown at the top of the Swagger UI page.
                        // ES: Título mostrado en la parte superior de la página Swagger UI.
                        .title("Tenpo Transactions API")

                        // EN: Short description of the API's purpose.
                        // ES: Descripción breve del propósito de la API.
                        .description("REST API used to manage Tenpista transactions for the challenge. "
                                + "All /api/** endpoints require the X-API-Key header.")

                        // EN: Semantic version; bump when the contract changes.
                        // ES: Versión semántica; incrementar cuando el contrato cambie.
                        .version("1.0.0")

                        .contact(new Contact().name("Challenge Implementation"))
                        .license(new License().name("Challenge use only")))

                // EN: Register the security scheme so it appears in the Swagger UI Authorize dialog.
                // ES: Registramos el esquema de seguridad para que aparezca en el diálogo Authorize de Swagger UI.
                .components(new Components().addSecuritySchemes(API_KEY_SCHEME, apiKeyScheme))

                // EN: Apply the security requirement globally — all operations require the API key
                //     unless they individually override it with @SecurityRequirement(name="").
                // ES: Aplicamos el requisito de seguridad globalmente — todas las operaciones requieren
                //     la API key a menos que individualmente lo sobreescriban con @SecurityRequirement(name="").
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
