package com.tenpo.challenge;

import com.tenpo.challenge.config.CorsProperties;
import com.tenpo.challenge.rate.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * EN: Spring Boot application entry point for the Tenpo Backend.
 *     {@code @SpringBootApplication} is a convenience annotation that combines:
 *       - {@code @Configuration}      : marks this as a bean-definition source
 *       - {@code @EnableAutoConfiguration}: activates Spring Boot's auto-configuration
 *       - {@code @ComponentScan}       : scans the current package and sub-packages for beans
 *
 *     {@code @EnableConfigurationProperties} explicitly registers the two typed properties
 *     records ({@link CorsProperties} and {@link RateLimitProperties}) so that Spring Boot
 *     binds {@code application.yml} values into them at startup and validates the constraints
 *     declared with Jakarta Bean Validation annotations.
 *
 *     The application can be started locally with:
 *       {@code ./mvnw spring-boot:run}
 *     or packaged as a fat JAR and run with:
 *       {@code java -jar target/tenpoback-*.jar}
 *
 * ES: Punto de entrada de la aplicación Spring Boot para el Backend de Tenpo.
 *     {@code @SpringBootApplication} es una anotación conveniente que combina:
 *       - {@code @Configuration}         : marca esto como fuente de definiciones de beans
 *       - {@code @EnableAutoConfiguration}: activa la auto-configuración de Spring Boot
 *       - {@code @ComponentScan}          : escanea el paquete actual y sub-paquetes para beans
 *
 *     {@code @EnableConfigurationProperties} registra explícitamente los dos registros de
 *     propiedades tipadas ({@link CorsProperties} y {@link RateLimitProperties}) para que
 *     Spring Boot vincule los valores de {@code application.yml} a ellos al inicio y valide
 *     las restricciones declaradas con anotaciones Jakarta Bean Validation.
 */
@SpringBootApplication
@EnableConfigurationProperties({CorsProperties.class, RateLimitProperties.class})
public class TenpobackApplication {

    /**
     * EN: Standard Java main method — delegates entirely to Spring Boot's launcher,
     *     which bootstraps the embedded Tomcat server and wires the application context.
     *
     * ES: Método principal Java estándar — delega completamente al lanzador de Spring Boot,
     *     que arranca el servidor Tomcat embebido y conecta el contexto de la aplicación.
     *
     * @param args command-line arguments forwarded to Spring Boot / argumentos de línea de comandos reenviados a Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(TenpobackApplication.class, args);
    }
}
