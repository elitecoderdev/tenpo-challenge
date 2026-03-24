package com.tenpo.challenge.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorsPropertiesTest {

    @Test
    void shouldNormalizeConfiguredOrigins() {
        CorsProperties corsProperties = new CorsProperties(List.of(
                " https://tenpo-challenge.vercel.app/ ",
                "http://localhost:5173",
                "http://localhost:5173/",
                "   "
        ));

        assertThat(corsProperties.allowedOrigins()).containsExactly(
                "https://tenpo-challenge.vercel.app",
                "http://localhost:5173"
        );
    }
}
