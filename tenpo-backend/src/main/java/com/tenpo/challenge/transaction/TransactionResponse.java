package com.tenpo.challenge.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * EN: Immutable response DTO returned by every transaction endpoint.
 *     Declared as a Java {@code record} to guarantee immutability without boilerplate.
 *     This DTO is the only representation of transaction data that leaves the service boundary
 *     — it never exposes internal fields like {@code customerNameNormalized}.
 *
 *     Notice that {@code amountInPesos} is returned as {@code int} (not {@code long}).
 *     This is safe because the service has already validated that the persisted value
 *     fits within the integer range.
 *
 * ES: DTO de respuesta inmutable devuelto por cada endpoint de transacción.
 *     Declarado como {@code record} de Java para garantizar inmutabilidad sin código repetitivo.
 *     Este DTO es la única representación de datos de transacción que sale del límite del servicio
 *     — nunca expone campos internos como {@code customerNameNormalized}.
 *
 *     Nótese que {@code amountInPesos} se devuelve como {@code int} (no {@code long}).
 *     Esto es seguro porque el servicio ya validó que el valor persistido
 *     cabe dentro del rango entero.
 *
 * Design — SOLID:
 *   SRP : Only represents the API response contract; carries no logic.
 */
@Schema(description = "Transaction representation returned by the API.")
public record TransactionResponse(

        // EN: Database-generated surrogate key for the transaction.
        // ES: Clave sustituta generada por la base de datos para la transacción.
        @Schema(example = "14")
        Integer id,

        // EN: Persisted amount in Chilean pesos. Always >= 0 (enforced by DB CHECK constraint + bean validation).
        // ES: Monto persistido en pesos chilenos. Siempre >= 0 (forzado por restricción CHECK de BD + bean validation).
        @Schema(example = "15000")
        int amountInPesos,

        // EN: Sanitized merchant name as stored; may differ from raw input (whitespace was collapsed).
        // ES: Nombre del comercio sanitizado como se almacenó; puede diferir del input original (espacios colapsados).
        @Schema(example = "Supermercado Lider")
        String merchant,

        // EN: Display name for the Tenpista, with normalized whitespace (but original casing).
        // ES: Nombre visible del Tenpista, con espacios normalizados (pero mayúsculas originales).
        @Schema(example = "Camila Torres")
        String customerName,

        // EN: Date and time of the transaction. Serialized as ISO-8601 by Jackson.
        //     The frontend (dayjs) formats this into a locale-friendly string for display.
        // ES: Fecha y hora de la transacción. Serializado como ISO-8601 por Jackson.
        //     El frontend (dayjs) formatea esto en una cadena amigable para el locale y la visualización.
        @Schema(example = "2026-03-07T11:30:00")
        LocalDateTime transactionDate
) {
}
