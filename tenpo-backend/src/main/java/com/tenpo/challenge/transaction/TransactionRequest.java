package com.tenpo.challenge.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

@Schema(description = "Payload used to create or update a transaction.")
public record TransactionRequest(
        @Schema(description = "Transaction amount in Chilean pesos.", example = "15000")
        @PositiveOrZero(message = "Transaction amount cannot be negative.")
        @Max(value = Integer.MAX_VALUE, message = "Transaction amount must stay within the supported integer range.")
        long amountInPesos,

        @Schema(description = "Merchant or transaction category.", example = "Supermercado Lider")
        @NotBlank(message = "Merchant is required.")
        @Size(max = 160, message = "Merchant must be shorter than 160 characters.")
        String merchant,

        @Schema(description = "Tenpista name.", example = "Camila Torres")
        @NotBlank(message = "Customer name is required.")
        @Size(max = 120, message = "Customer name must be shorter than 120 characters.")
        String customerName,

        @Schema(description = "Transaction date and time.", example = "2026-03-07T11:30:00")
        @NotNull(message = "Transaction date is required.")
        @PastOrPresent(message = "Transaction date cannot be in the future.")
        LocalDateTime transactionDate
) {
}
