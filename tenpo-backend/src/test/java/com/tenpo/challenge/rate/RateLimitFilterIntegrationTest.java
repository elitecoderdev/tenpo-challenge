package com.tenpo.challenge.rate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.rate-limit.capacity=3",
        "app.rate-limit.duration=PT1M"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldBlockFourthRequestWithinTheSameMinute() throws Exception {
        for (int requestNumber = 0; requestNumber < 3; requestNumber++) {
            mockMvc.perform(get("/api/transactions").header("X-Forwarded-For", "10.0.0.1"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Rate-Limit-Limit", "3"));
        }

        mockMvc.perform(get("/api/transactions").header("X-Forwarded-For", "10.0.0.1"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(429));
    }
}
