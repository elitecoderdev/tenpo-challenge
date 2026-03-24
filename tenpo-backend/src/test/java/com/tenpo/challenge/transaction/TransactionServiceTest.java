package com.tenpo.challenge.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tenpo.challenge.shared.exception.BusinessRuleException;
import com.tenpo.challenge.shared.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * EN: Unit tests for {@link TransactionService} using Mockito.
 *     The repository and mapper are mocked so these tests exercise only the service logic:
 *     quota enforcement, input sanitization, entity population, and error conditions.
 *     No Spring context or database is required, keeping tests fast and deterministic.
 *
 * ES: Pruebas unitarias para {@link TransactionService} usando Mockito.
 *     El repositorio y el mapper son mockeados para que estas pruebas ejerciten solo la lógica del servicio:
 *     cumplimiento de cuota, saneamiento de entrada, llenado de entidades y condiciones de error.
 *     No se requiere contexto Spring ni base de datos, manteniendo las pruebas rápidas y deterministas.
 */
@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    // EN: Mocked collaborators — no actual database I/O occurs in these tests.
    // ES: Colaboradores mockeados — no ocurre I/O de base de datos real en estas pruebas.
    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionMapper transactionMapper;

    // EN: Injects mocks into the service constructor automatically.
    // ES: Inyecta los mocks en el constructor del servicio automáticamente.
    @InjectMocks
    private TransactionService transactionService;

    // EN: Captures the Transaction instance passed to repository.save() for assertion.
    // ES: Captura la instancia Transaction pasada a repository.save() para aserción.
    @Captor
    private ArgumentCaptor<Transaction> transactionCaptor;

    // EN: Shared request fixture with intentional leading/trailing/extra spaces
    //     so that tests can verify whitespace normalization.
    // ES: Fixture de solicitud compartido con espacios iniciales/finales/extra intencionales
    //     para que las pruebas puedan verificar la normalización de espacios.
    private TransactionRequest transactionRequest;

    @BeforeEach
    void setUp() {
        // EN: The raw input has intentional extra whitespace to test sanitization.
        // ES: La entrada cruda tiene espacios extra intencionales para probar el saneamiento.
        transactionRequest = new TransactionRequest(
                20000,
                "  Supermercado Lider  ",
                "  Camila    Torres ",
                LocalDateTime.of(2026, 3, 7, 10, 30)
        );
    }

    // ── Create Transaction Tests ──────────────────────────────────────────────────────────

    /**
     * EN: Verifies that a transaction is created and persisted when the customer still
     *     has available quota (99 existing transactions, limit is 100).
     *     Also confirms that whitespace in both merchant and customer name is normalized.
     *
     * ES: Verifica que una transacción se crea y persiste cuando el cliente aún tiene
     *     cuota disponible (99 transacciones existentes, límite es 100).
     *     También confirma que los espacios en el nombre del comercio y del cliente se normalizan.
     */
    @Test
    void shouldCreateTransactionWhenCustomerStillHasCapacity() {
        Transaction savedTransaction = new Transaction();
        savedTransaction.setAmountInPesos(Math.toIntExact(transactionRequest.amountInPesos()));
        savedTransaction.setMerchant("Supermercado Lider");
        savedTransaction.setCustomerName("Camila Torres");
        savedTransaction.setTransactionDate(transactionRequest.transactionDate());

        // EN: Stub count to 99 — one below the 100-transaction limit.
        // ES: Definimos el conteo en 99 — uno por debajo del límite de 100 transacciones.
        when(transactionRepository.countByCustomerNameNormalized("camila torres")).thenReturn(99L);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toResponse(savedTransaction)).thenReturn(
                new TransactionResponse(1, 20000, "Supermercado Lider", "Camila Torres", transactionRequest.transactionDate())
        );

        TransactionResponse response = transactionService.createTransaction(transactionRequest);

        // EN: Capture the entity passed to save() and assert its fields were sanitized correctly.
        // ES: Capturamos la entidad pasada a save() y verificamos que sus campos fueron sanitizados correctamente.
        verify(transactionRepository).save(transactionCaptor.capture());
        Transaction persistedTransaction = transactionCaptor.getValue();
        assertThat(persistedTransaction.getMerchant()).isEqualTo("Supermercado Lider");
        assertThat(persistedTransaction.getCustomerName()).isEqualTo("Camila Torres");
        assertThat(persistedTransaction.getAmountInPesos()).isEqualTo(20000);
        assertThat(response.id()).isEqualTo(1);
    }

    /**
     * EN: Verifies that the 100-transaction quota is enforced:
     *     when the customer already has 100 transactions, createTransaction must throw
     *     {@link BusinessRuleException} and NOT call repository.save().
     *
     * ES: Verifica que se hace cumplir la cuota de 100 transacciones:
     *     cuando el cliente ya tiene 100 transacciones, createTransaction debe lanzar
     *     {@link BusinessRuleException} y NO llamar a repository.save().
     */
    @Test
    void shouldRejectCreateWhenCustomerAlreadyHasOneHundredTransactions() {
        // EN: Stub count to exactly 100 — at the limit, the next create must be rejected.
        // ES: Definimos el conteo en exactamente 100 — en el límite, la siguiente creación debe ser rechazada.
        when(transactionRepository.countByCustomerNameNormalized("camila torres")).thenReturn(100L);

        assertThatThrownBy(() -> transactionService.createTransaction(transactionRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A Tenpista can only have up to 100 transactions.");

        // EN: Verify save() was never called — no partial writes should occur on quota violation.
        // ES: Verificamos que save() nunca fue llamado — no deben ocurrir escrituras parciales en violación de cuota.
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ── Get Transaction Tests ─────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that getTransaction throws {@link ResourceNotFoundException}
     *     when the requested id does not exist in the repository.
     *     This is the "not found" contract — HTTP 404 in production.
     *
     * ES: Verifica que getTransaction lanza {@link ResourceNotFoundException}
     *     cuando el id solicitado no existe en el repositorio.
     *     Este es el contrato "no encontrado" — HTTP 404 en producción.
     */
    @Test
    void shouldThrowWhenTransactionDoesNotExist() {
        when(transactionRepository.findById(44)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getTransaction(44))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Transaction with id 44 was not found.");
    }

    // ── List / Filter Tests ───────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that listTransactions applies the canonical name transformation
     *     before querying the repository, so input with extra spaces still finds the
     *     correct transactions.
     *
     * ES: Verifica que listTransactions aplica la transformación de nombre canónico
     *     antes de consultar el repositorio, para que la entrada con espacios extra
     *     aún encuentre las transacciones correctas.
     */
    @Test
    void shouldFilterByCustomerName() {
        Transaction transaction = new Transaction();
        transaction.setAmountInPesos(1000);
        transaction.setMerchant("Farmacia");
        transaction.setCustomerName("Camila Torres");
        transaction.setTransactionDate(LocalDateTime.of(2026, 3, 6, 9, 0));

        TransactionResponse expected = new TransactionResponse(7, 1000, "Farmacia", "Camila Torres", transaction.getTransactionDate());

        // EN: The service must canonicalize " Camila   Torres " → "camila torres" before querying.
        // ES: El servicio debe canonizar " Camila   Torres " → "camila torres" antes de consultar.
        when(transactionRepository.findAllByCustomerNameNormalizedOrderByTransactionDateDescIdDesc("camila torres"))
                .thenReturn(List.of(transaction));
        when(transactionMapper.toResponse(transaction)).thenReturn(expected);

        List<TransactionResponse> response = transactionService.listTransactions(" Camila   Torres ");

        assertThat(response).containsExactly(expected);
    }

    // ── Update Transaction Tests ──────────────────────────────────────────────────────────

    /**
     * EN: Verifies that updateTransaction succeeds without performing a quota COUNT query
     *     when the customer name does not change between the existing record and the request.
     *     Same customer → the total count cannot increase → no quota check needed.
     *
     * ES: Verifica que updateTransaction tenga éxito sin realizar una consulta COUNT de cuota
     *     cuando el nombre del cliente no cambia entre el registro existente y la solicitud.
     *     Mismo cliente → el conteo total no puede aumentar → no se necesita verificación de cuota.
     */
    @Test
    void shouldUpdateTransactionWithoutQuotaCheckWhenCustomerIsSame() {
        // EN: Existing record already belongs to "Camila Torres".
        // ES: El registro existente ya pertenece a "Camila Torres".
        Transaction existing = new Transaction();
        existing.setAmountInPesos(10000);
        existing.setMerchant("Old Merchant");
        existing.setCustomerName("Camila Torres");
        existing.setCustomerNameNormalized("camila torres");
        existing.setTransactionDate(LocalDateTime.of(2026, 3, 1, 10, 0));

        when(transactionRepository.findById(5)).thenReturn(Optional.of(existing));
        when(transactionRepository.save(any(Transaction.class))).thenReturn(existing);
        when(transactionMapper.toResponse(existing)).thenReturn(
                new TransactionResponse(5, 20000, "Supermercado Lider", "Camila Torres", transactionRequest.transactionDate())
        );

        TransactionResponse response = transactionService.updateTransaction(5, transactionRequest);

        // EN: Same customer name → count query must NOT be issued (no extra DB round-trip).
        // ES: Mismo nombre de cliente → la consulta de conteo NO debe emitirse (sin viaje extra a la BD).
        verify(transactionRepository, never()).countByCustomerNameNormalized(any());
        assertThat(response.id()).isEqualTo(5);
    }

    /**
     * EN: Verifies that updateTransaction re-checks the quota when the customer name changes
     *     and allows the update if the new customer still has available capacity.
     *
     * ES: Verifica que updateTransaction re-verifica la cuota cuando el nombre del cliente cambia
     *     y permite la actualización si el nuevo cliente aún tiene capacidad disponible.
     */
    @Test
    void shouldUpdateTransactionWhenCustomerChangesAndNewCustomerHasCapacity() {
        // EN: Existing record belongs to "Jose Perez"; request reassigns it to "Camila Torres".
        // ES: El registro existente pertenece a "Jose Perez"; la solicitud lo reasigna a "Camila Torres".
        Transaction existing = new Transaction();
        existing.setAmountInPesos(10000);
        existing.setMerchant("Old Merchant");
        existing.setCustomerName("Jose Perez");
        existing.setCustomerNameNormalized("jose perez");
        existing.setTransactionDate(LocalDateTime.of(2026, 3, 1, 10, 0));

        when(transactionRepository.findById(5)).thenReturn(Optional.of(existing));
        when(transactionRepository.countByCustomerNameNormalized("camila torres")).thenReturn(5L);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(existing);
        when(transactionMapper.toResponse(existing)).thenReturn(
                new TransactionResponse(5, 20000, "Supermercado Lider", "Camila Torres", transactionRequest.transactionDate())
        );

        TransactionResponse response = transactionService.updateTransaction(5, transactionRequest);

        // EN: Customer changed → quota must have been checked for the new customer name.
        // ES: Cliente cambió → la cuota debe haber sido verificada para el nuevo nombre de cliente.
        verify(transactionRepository).countByCustomerNameNormalized("camila torres");
        assertThat(response.id()).isEqualTo(5);
    }

    /**
     * EN: Verifies that updateTransaction throws {@link BusinessRuleException} when the
     *     customer name changes to a Tenpista who has already reached the 100-transaction limit.
     *     The existing record must not be modified (save() must not be called).
     *
     * ES: Verifica que updateTransaction lanza {@link BusinessRuleException} cuando el nombre
     *     del cliente cambia a un Tenpista que ya alcanzó el límite de 100 transacciones.
     *     El registro existente no debe modificarse (save() no debe llamarse).
     */
    @Test
    void shouldRejectUpdateWhenNewCustomerHasReachedQuota() {
        Transaction existing = new Transaction();
        existing.setAmountInPesos(10000);
        existing.setMerchant("Old Merchant");
        existing.setCustomerName("Jose Perez");
        existing.setCustomerNameNormalized("jose perez");
        existing.setTransactionDate(LocalDateTime.of(2026, 3, 1, 10, 0));

        when(transactionRepository.findById(5)).thenReturn(Optional.of(existing));
        when(transactionRepository.countByCustomerNameNormalized("camila torres")).thenReturn(100L);

        assertThatThrownBy(() -> transactionService.updateTransaction(5, transactionRequest))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("A Tenpista can only have up to 100 transactions.");

        // EN: Quota exceeded → save() must never be called; the record remains unchanged.
        // ES: Cuota excedida → save() nunca debe llamarse; el registro permanece sin cambios.
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    // ── Delete Transaction Tests ───────────────────────────────────────────────────────────

    /**
     * EN: Verifies that deleteTransaction loads the record and then calls repository.delete()
     *     when the id exists.
     *
     * ES: Verifica que deleteTransaction carga el registro y luego llama a repository.delete()
     *     cuando el id existe.
     */
    @Test
    void shouldDeleteTransactionSuccessfully() {
        Transaction existing = new Transaction();
        existing.setAmountInPesos(5000);
        existing.setMerchant("Farmacia");
        existing.setCustomerName("Camila Torres");
        existing.setTransactionDate(LocalDateTime.of(2026, 3, 6, 9, 0));

        when(transactionRepository.findById(7)).thenReturn(Optional.of(existing));

        transactionService.deleteTransaction(7);

        // EN: The exact entity loaded from the repository must be passed to delete().
        // ES: La entidad exacta cargada del repositorio debe pasarse a delete().
        verify(transactionRepository).delete(existing);
    }

    /**
     * EN: Verifies that deleteTransaction throws {@link ResourceNotFoundException} when the
     *     requested id does not exist, and that delete() is never called in that case.
     *
     * ES: Verifica que deleteTransaction lanza {@link ResourceNotFoundException} cuando el
     *     id solicitado no existe, y que delete() nunca se llama en ese caso.
     */
    @Test
    void shouldThrowWhenDeletingNonExistentTransaction() {
        when(transactionRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.deleteTransaction(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Transaction with id 99 was not found.");

        // EN: Guard: delete() must not be called when the record does not exist.
        // ES: Guardia: delete() no debe llamarse cuando el registro no existe.
        verify(transactionRepository, never()).delete(any(Transaction.class));
    }

    // ── Amount Range Tests ────────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that an amount equal to Integer.MAX_VALUE is accepted and stored correctly.
     *     This is the boundary case for the long → int conversion in applyRequest().
     *     Math.toIntExact would throw ArithmeticException for out-of-range values,
     *     but Integer.MAX_VALUE is exactly at the boundary so it must succeed.
     *
     * ES: Verifica que un monto igual a Integer.MAX_VALUE es aceptado y almacenado correctamente.
     *     Este es el caso límite para la conversión long → int en applyRequest().
     *     Math.toIntExact lanzaría ArithmeticException para valores fuera de rango,
     *     pero Integer.MAX_VALUE está exactamente en el límite por lo que debe tener éxito.
     */
    @Test
    void shouldConvertValidatedLongAmountToIntegerBeforeSaving() {
        TransactionRequest largeButValidRequest = new TransactionRequest(
                Integer.MAX_VALUE,
                "Supermercado Lider",
                "Camila Torres",
                LocalDateTime.of(2026, 3, 7, 10, 30)
        );

        Transaction savedTransaction = new Transaction();
        savedTransaction.setAmountInPesos(Integer.MAX_VALUE);
        savedTransaction.setMerchant("Supermercado Lider");
        savedTransaction.setCustomerName("Camila Torres");
        savedTransaction.setTransactionDate(largeButValidRequest.transactionDate());

        when(transactionRepository.countByCustomerNameNormalized("camila torres")).thenReturn(0L);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(savedTransaction);
        when(transactionMapper.toResponse(savedTransaction)).thenReturn(
                new TransactionResponse(1, Integer.MAX_VALUE, "Supermercado Lider", "Camila Torres", largeButValidRequest.transactionDate())
        );

        transactionService.createTransaction(largeButValidRequest);

        verify(transactionRepository).save(transactionCaptor.capture());
        // EN: Confirm Integer.MAX_VALUE survived the long → int conversion unchanged.
        // ES: Confirmamos que Integer.MAX_VALUE sobrevivió la conversión long → int sin cambios.
        assertThat(transactionCaptor.getValue().getAmountInPesos()).isEqualTo(Integer.MAX_VALUE);
    }
}
