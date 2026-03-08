package com.tenpo.challenge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI tenpoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tenpo Transactions API")
                        .description("REST API used to manage Tenpista transactions for the challenge.")
                        .version("1.0.0")
                        .contact(new Contact().name("Challenge Implementation"))
                        .license(new License().name("Challenge use only")));
    }
}
