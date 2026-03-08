package com.tenpo.challenge.transaction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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

@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "CRUD operations for Tenpista transactions.")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    @Operation(summary = "List transactions", description = "Returns all transactions or filters them by Tenpista name.")
    public List<TransactionResponse> listTransactions(
            @RequestParam(required = false) String customerName
    ) {
        return transactionService.listTransactions(customerName);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get a transaction", description = "Returns one transaction by identifier.")
    public TransactionResponse getTransaction(@PathVariable Integer transactionId) {
        return transactionService.getTransaction(transactionId);
    }

    @PostMapping
    @Operation(summary = "Create a transaction", description = "Creates a new Tenpista transaction.")
    public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionRequest request) {
        TransactionResponse createdTransaction = transactionService.createTransaction(request);
        return ResponseEntity
                .created(URI.create("/api/transactions/" + createdTransaction.id()))
                .body(createdTransaction);
    }

    @PutMapping("/{transactionId}")
    @Operation(summary = "Update a transaction", description = "Updates an existing transaction.")
    public TransactionResponse updateTransaction(
            @PathVariable Integer transactionId,
            @Valid @RequestBody TransactionRequest request
    ) {
        return transactionService.updateTransaction(transactionId, request);
    }

    @DeleteMapping("/{transactionId}")
    @Operation(summary = "Delete a transaction", description = "Deletes an existing transaction.")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Integer transactionId) {
        transactionService.deleteTransaction(transactionId);
        return ResponseEntity.noContent().build();
    }
}
