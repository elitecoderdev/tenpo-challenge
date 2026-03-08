package com.tenpo.challenge;

import com.tenpo.challenge.config.CorsProperties;
import com.tenpo.challenge.rate.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({CorsProperties.class, RateLimitProperties.class})
public class TenpobackApplication {

    public static void main(String[] args) {
        SpringApplication.run(TenpobackApplication.class, args);
    }
}
