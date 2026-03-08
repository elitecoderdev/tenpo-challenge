package com.tenpo.challenge.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tenpo.challenge.shared.api.ApiErrorFactory;
import com.tenpo.challenge.shared.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock
    private TransactionService transactionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TransactionController(transactionService))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiErrorFactory()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void shouldReturnTransactions() throws Exception {
        when(transactionService.listTransactions(null)).thenReturn(List.of(
                new TransactionResponse(10, 3000, "Farmacia", "Camila Torres", LocalDateTime.of(2026, 3, 7, 8, 0))
        ));

        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].merchant").value("Farmacia"));
    }

    @Test
    void shouldCreateTransactionAndReturnLocationHeader() throws Exception {
        when(transactionService.createTransaction(any(TransactionRequest.class))).thenReturn(
                new TransactionResponse(14, 15000, "Supermercado Lider", "Camila Torres", LocalDateTime.of(2026, 3, 6, 11, 30))
        );

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountInPesos": 15000,
                                  "merchant": "Supermercado Lider",
                                  "customerName": "Camila Torres",
                                  "transactionDate": "2026-03-06T11:30:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/transactions/14"))
                .andExpect(jsonPath("$.id").value(14));
    }

    @Test
    void shouldReturnStructuredValidationErrors() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountInPesos": -10,
                                  "merchant": "",
                                  "customerName": "",
                                  "transactionDate": "2099-03-07T11:30:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.length()").value(4));
    }

    @Test
    void shouldReturnValidationErrorWhenAmountExceedsIntegerRange() throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amountInPesos": 2345539849839,
                                  "merchant": "Superman",
                                  "customerName": "Camila Torris",
                                  "transactionDate": "2026-03-07T07:08:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amountInPesos"));
    }
}
