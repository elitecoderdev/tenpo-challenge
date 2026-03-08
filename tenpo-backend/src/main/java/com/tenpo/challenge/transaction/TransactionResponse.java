package com.tenpo.challenge.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Transaction representation returned by the API.")
public record TransactionResponse(
        @Schema(example = "14")
        Integer id,
        @Schema(example = "15000")
        int amountInPesos,
        @Schema(example = "Supermercado Lider")
        String merchant,
        @Schema(example = "Camila Torres")
        String customerName,
        @Schema(example = "2026-03-07T11:30:00")
        LocalDateTime transactionDate
) {
}
