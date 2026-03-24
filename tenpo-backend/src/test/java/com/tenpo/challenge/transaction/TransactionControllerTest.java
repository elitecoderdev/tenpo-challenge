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

/**
 * EN: Unit tests for {@link TransactionController} using MockMvc in standalone mode.
 *     The service is mocked (Mockito) so these tests exercise only the HTTP layer:
 *     routing, serialization, validation, and status codes.
 *     No Spring context or database is started, making tests fast and isolated.
 *
 * ES: Pruebas unitarias para {@link TransactionController} usando MockMvc en modo standalone.
 *     El servicio es mockeado (Mockito) para que estas pruebas solo ejerciten la capa HTTP:
 *     enrutamiento, serialización, validación y códigos de estado.
 *     No se inicia contexto Spring ni base de datos, haciendo las pruebas rápidas y aisladas.
 */
@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    // EN: Mocked service; all interactions are stubbed per test.
    // ES: Servicio mockeado; todas las interacciones se definen por prueba.
    @Mock
    private TransactionService transactionService;

    // EN: MockMvc instance configured in standalone mode (no full Spring context required).
    // ES: Instancia MockMvc configurada en modo standalone (no se requiere contexto Spring completo).
    private MockMvc mockMvc;

    /**
     * EN: Builds the MockMvc instance with the controller under test and wires:
     *       - GlobalExceptionHandler: so validation and domain errors produce structured responses
     *       - MappingJackson2HttpMessageConverter: so date/time types serialize/deserialize correctly
     *     This setup is equivalent to what Spring Boot's test slice (@WebMvcTest) would provide,
     *     but without spinning up the full application context.
     *
     * ES: Construye la instancia MockMvc con el controlador bajo prueba y conecta:
     *       - GlobalExceptionHandler: para que los errores de validación y dominio produzcan respuestas estructuradas
     *       - MappingJackson2HttpMessageConverter: para que los tipos de fecha/hora serialicen/deserialicen correctamente
     */
    @BeforeEach
    void setUp() {
        // EN: Register Jackson modules (e.g. JavaTimeModule) so LocalDateTime fields serialize correctly.
        // ES: Registramos los módulos Jackson (ej. JavaTimeModule) para que los campos LocalDateTime serialicen correctamente.
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TransactionController(transactionService))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiErrorFactory()))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    // ── Happy Path Tests ──────────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that GET /api/transactions returns HTTP 200 and the JSON list
     *     when the service returns a non-empty collection.
     *
     * ES: Verifica que GET /api/transactions devuelva HTTP 200 y la lista JSON
     *     cuando el servicio devuelve una colección no vacía.
     */
    @Test
    void shouldReturnTransactions() throws Exception {
        // EN: Arrange — stub the service to return a single transaction.
        // ES: Preparación — definimos que el servicio devuelva una sola transacción.
        when(transactionService.listTransactions(null)).thenReturn(List.of(
                new TransactionResponse(10, 3000, "Farmacia", "Camila Torres", LocalDateTime.of(2026, 3, 7, 8, 0))
        ));

        // EN: Act & Assert — perform the request and verify response fields.
        // ES: Actuación y Verificación — realizamos la solicitud y verificamos los campos de respuesta.
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10))
                .andExpect(jsonPath("$[0].merchant").value("Farmacia"));
    }

    /**
     * EN: Verifies that POST /api/transactions returns HTTP 201 Created with a
     *     Location header pointing to the new resource URL.
     *
     * ES: Verifica que POST /api/transactions devuelva HTTP 201 Created con un
     *     encabezado Location apuntando a la URL del nuevo recurso.
     */
    @Test
    void shouldCreateTransactionAndReturnLocationHeader() throws Exception {
        // EN: Arrange — stub the service to return a response with id=14.
        // ES: Preparación — definimos que el servicio devuelva una respuesta con id=14.
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
                // EN: Verify the Location header points to the new resource.
                // ES: Verificamos que el encabezado Location apunte al nuevo recurso.
                .andExpect(header().string("Location", "/api/transactions/14"))
                .andExpect(jsonPath("$.id").value(14));
    }

    // ── Validation Tests ──────────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that a payload with all four constraint violations (negative amount,
     *     blank merchant, blank customerName, future date) produces HTTP 400 with
     *     exactly four field errors in the ApiError body.
     *
     * ES: Verifica que un payload con las cuatro violaciones de restricción (monto negativo,
     *     comercio en blanco, customerName en blanco, fecha futura) produce HTTP 400 con
     *     exactamente cuatro errores de campo en el cuerpo ApiError.
     */
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
                // EN: Four violations: negative amount, blank merchant, blank customerName, future date.
                // ES: Cuatro violaciones: monto negativo, comercio en blanco, customerName en blanco, fecha futura.
                .andExpect(jsonPath("$.fieldErrors.length()").value(4));
    }

    /**
     * EN: Verifies that an amount larger than Integer.MAX_VALUE triggers the @Max validation
     *     and results in a 400 Bad Request with a field error on "amountInPesos".
     *     This test is important because the DTO uses 'long' to accept large JSON numbers
     *     without a parse error, relying on @Max to reject out-of-range values.
     *
     * ES: Verifica que un monto mayor que Integer.MAX_VALUE activa la validación @Max
     *     y resulta en un 400 Bad Request con un error de campo en "amountInPesos".
     *     Esta prueba es importante porque el DTO usa 'long' para aceptar números JSON grandes
     *     sin error de análisis, dependiendo de @Max para rechazar valores fuera de rango.
     */
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
                // EN: Field error must specifically identify "amountInPesos" so the frontend
                //     can display the error message next to the correct form field.
                // ES: El error de campo debe identificar específicamente "amountInPesos" para que
                //     el frontend pueda mostrar el mensaje de error junto al campo de formulario correcto.
                .andExpect(jsonPath("$.fieldErrors[0].field").value("amountInPesos"));
    }
}
