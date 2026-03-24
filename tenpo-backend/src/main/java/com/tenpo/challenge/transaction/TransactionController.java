package com.tenpo.challenge.transaction;

import com.tenpo.challenge.shared.api.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EN: REST controller that exposes the public transaction API under {@code /api/transactions}.
 *     Follows the Single Responsibility Principle: this class is only responsible for
 *     HTTP concerns (routing, request deserialization, response serialization, status codes).
 *     All business logic is delegated to {@link TransactionService}.
 *     All validation annotations on the request body are enforced by Spring's
 *     {@code @Valid} integration; constraint failures are caught by
 *     {@code GlobalExceptionHandler} and returned as structured {@code ApiError} payloads.
 *
 * ES: Controlador REST que expone la API pública de transacciones en {@code /api/transactions}.
 *     Sigue el Principio de Responsabilidad Única: esta clase solo es responsable de las
 *     preocupaciones HTTP (enrutamiento, deserialización de solicitudes, serialización de respuestas,
 *     códigos de estado). Toda la lógica de negocio se delega a {@link TransactionService}.
 *     Las anotaciones de validación en el cuerpo de la solicitud son aplicadas por la integración
 *     {@code @Valid} de Spring; los fallos de restricción son capturados por
 *     {@code GlobalExceptionHandler} y devueltos como payloads {@code ApiError} estructurados.
 *
 * Design — SOLID:
 *   SRP : HTTP concerns only; business logic is in TransactionService.
 *   DIP : Depends on the TransactionService abstraction via constructor injection.
 */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "CRUD operations for Tenpista transactions.")
public class TransactionController {

    // EN: Service injected via constructor (Dependency Inversion Principle).
    //     Constructor injection is preferred over field injection because it makes
    //     dependencies explicit and facilitates unit testing without a Spring context.
    // ES: Servicio inyectado via constructor (Principio de Inversión de Dependencias).
    //     La inyección por constructor es preferida sobre la inyección de campo porque hace
    //     las dependencias explícitas y facilita las pruebas unitarias sin contexto de Spring.
    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    // ── GET /api/transactions ─────────────────────────────────────────────────────────────

    /**
     * EN: Returns all transactions ordered by date descending, then by id descending.
     *     If {@code customerName} is provided and non-blank, the result is filtered to
     *     transactions belonging to that Tenpista (case-insensitive, whitespace-normalized).
     *     The frontend passes this parameter to support server-side filtering;
     *     the UI also performs local in-memory filtering to avoid API quota consumption.
     *
     * ES: Devuelve todas las transacciones ordenadas por fecha descendente, luego por id descendente.
     *     Si se proporciona {@code customerName} y no está en blanco, el resultado se filtra a
     *     transacciones pertenecientes a ese Tenpista (insensible a mayúsculas, espacios normalizados).
     *     El frontend pasa este parámetro para soportar filtrado del lado del servidor;
     *     la UI también realiza filtrado local en memoria para evitar consumir la cuota de la API.
     *
     * @param customerName optional Tenpista name filter / filtro de nombre de Tenpista opcional
     * @return list of matching transactions / lista de transacciones que coinciden
     */
    @GetMapping
    @Operation(summary = "List transactions", description = "Returns all transactions or filters them by Tenpista name.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transaction list returned successfully.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array = @ArraySchema(schema = @Schema(implementation = TransactionResponse.class))
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded (3 requests / minute / client).",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        )
    })
    public List<TransactionResponse> listTransactions(
            @RequestParam(required = false) String customerName
    ) {
        return transactionService.listTransactions(customerName);
    }

    // ── GET /api/transactions/{transactionId} ─────────────────────────────────────────────

    /**
     * EN: Returns a single transaction by its identifier.
     *     Returns {@code 404 Not Found} via {@code ResourceNotFoundException} if not found.
     *
     * ES: Devuelve una sola transacción por su identificador.
     *     Devuelve {@code 404 Not Found} via {@code ResourceNotFoundException} si no se encuentra.
     *
     * @param transactionId the transaction primary key / la clave primaria de la transacción
     * @return the matching transaction response / la respuesta de transacción correspondiente
     */
    @GetMapping("/{transactionId}")
    @Operation(summary = "Get a transaction", description = "Returns one transaction by identifier.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transaction found.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = TransactionResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Transaction not found.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded (3 requests / minute / client).",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        )
    })
    public TransactionResponse getTransaction(@PathVariable Integer transactionId) {
        return transactionService.getTransaction(transactionId);
    }

    // ── POST /api/transactions ────────────────────────────────────────────────────────────

    /**
     * EN: Creates a new Tenpista transaction.
     *     Returns {@code 201 Created} with a {@code Location} header pointing to the new resource.
     *     The {@code @Valid} annotation triggers Bean Validation before the service is called;
     *     any constraint violations are handled globally and returned as {@code 400 Bad Request}.
     *
     * ES: Crea una nueva transacción de Tenpista.
     *     Devuelve {@code 201 Created} con un encabezado {@code Location} apuntando al nuevo recurso.
     *     La anotación {@code @Valid} activa Bean Validation antes de llamar al servicio;
     *     cualquier violación de restricción es manejada globalmente y devuelta como {@code 400 Bad Request}.
     *
     * @param request the validated transaction payload / el payload de transacción validado
     * @return 201 response with the persisted transaction / respuesta 201 con la transacción persistida
     */
    @PostMapping
    @Operation(summary = "Create a transaction", description = "Creates a new Tenpista transaction.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Transaction created. The Location header points to the new resource.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = TransactionResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed. The fieldErrors array contains per-field messages.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded (3 requests / minute / client).",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        )
    })
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse createdTransaction = transactionService.createTransaction(request);

        // EN: Build the Location header URI pointing to the newly created resource.
        //     This follows the HTTP 201 Created contract (RFC 7231 §6.3.2).
        // ES: Construimos la URI del encabezado Location apuntando al recurso recién creado.
        //     Esto sigue el contrato HTTP 201 Created (RFC 7231 §6.3.2).
        return ResponseEntity
                .created(URI.create("/api/transactions/" + createdTransaction.id()))
                .body(createdTransaction);
    }

    // ── PUT /api/transactions/{transactionId} ─────────────────────────────────────────────

    /**
     * EN: Replaces the fields of an existing transaction (full update semantics).
     *     Returns {@code 200 OK} with the updated state.
     *     Returns {@code 404} if the transaction does not exist.
     *
     * ES: Reemplaza los campos de una transacción existente (semántica de actualización completa).
     *     Devuelve {@code 200 OK} con el estado actualizado.
     *     Devuelve {@code 404} si la transacción no existe.
     *
     * @param transactionId the id of the transaction to update / el id de la transacción a actualizar
     * @param request       the updated payload / el payload actualizado
     * @return the updated transaction / la transacción actualizada
     */
    @PutMapping("/{transactionId}")
    @Operation(summary = "Update a transaction", description = "Updates an existing transaction.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Transaction updated successfully.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = TransactionResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Validation failed. The fieldErrors array contains per-field messages.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Transaction not found.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded (3 requests / minute / client).",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        )
    })
    public TransactionResponse updateTransaction(
            @PathVariable Integer transactionId,
            @Valid @RequestBody TransactionRequest request
    ) {
        return transactionService.updateTransaction(transactionId, request);
    }

    // ── DELETE /api/transactions/{transactionId} ──────────────────────────────────────────

    /**
     * EN: Deletes an existing transaction by its identifier.
     *     Returns {@code 204 No Content} on success.
     *     Returns {@code 404} if the transaction does not exist.
     *
     * ES: Elimina una transacción existente por su identificador.
     *     Devuelve {@code 204 No Content} en caso de éxito.
     *     Devuelve {@code 404} si la transacción no existe.
     *
     * @param transactionId the id of the transaction to delete / el id de la transacción a eliminar
     * @return 204 empty response / respuesta vacía 204
     */
    @DeleteMapping("/{transactionId}")
    @Operation(summary = "Delete a transaction", description = "Deletes an existing transaction.")
    @ApiResponses({
        @ApiResponse(
            responseCode = "204",
            description = "Transaction deleted. No body is returned.",
            content = @Content
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Transaction not found.",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        ),
        @ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded (3 requests / minute / client).",
            content = @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class)
            )
        )
    })
    public ResponseEntity<Void> deleteTransaction(@PathVariable Integer transactionId) {
        transactionService.deleteTransaction(transactionId);
        return ResponseEntity.noContent().build();
    }
}
