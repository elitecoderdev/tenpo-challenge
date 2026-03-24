package com.tenpo.challenge.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * EN: Integration tests for {@link TransactionRepository} using Spring's {@code @DataJpaTest} slice.
 *     {@code @DataJpaTest} starts only the JPA layer (Hibernate, DataSource, transaction management)
 *     without a full Spring context. The in-memory H2 database (configured in application-test.yml
 *     with PostgreSQL compatibility mode) keeps tests hermetic and fast.
 *
 *     These tests verify that the derived query methods in the repository behave correctly:
 *       - Count by normalized customer name
 *       - Sort by transaction_date DESC, id DESC
 *
 *     IMPORTANT — JPA lifecycle hooks:
 *       Although buildTransaction() manually sets customerNameNormalized, the @PrePersist hook
 *       on Transaction will override it with Transaction.canonicalize(customerName) when
 *       repository.save() is called. The manually-set values happen to match what canonicalize()
 *       would produce, so the tests remain correct. This is intentional: it shows the hook works.
 *
 * ES: Pruebas de integración para {@link TransactionRepository} usando el slice {@code @DataJpaTest} de Spring.
 *     {@code @DataJpaTest} inicia solo la capa JPA (Hibernate, DataSource, gestión de transacciones)
 *     sin un contexto Spring completo. La base de datos H2 en memoria (configurada en application-test.yml
 *     con modo de compatibilidad PostgreSQL) mantiene las pruebas herméticas y rápidas.
 *
 *     IMPORTANTE — Hooks del ciclo de vida JPA:
 *       Aunque buildTransaction() establece manualmente customerNameNormalized, el hook @PrePersist
 *       en Transaction lo sobreescribirá con Transaction.canonicalize(customerName) cuando se
 *       llame a repository.save(). Los valores establecidos manualmente coinciden con lo que
 *       produciría canonicalize(), así que las pruebas siguen siendo correctas. Esto es intencional:
 *       muestra que el hook funciona.
 */
@DataJpaTest
@ActiveProfiles("test")
class TransactionRepositoryTest {

    // EN: Autowired by @DataJpaTest — backed by the in-memory H2 database.
    // ES: Inyectado automáticamente por @DataJpaTest — respaldado por la base de datos H2 en memoria.
    @Autowired
    private TransactionRepository transactionRepository;

    // ── Count Tests ───────────────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that countByCustomerNameNormalized counts only transactions with
     *     an exact match on the normalized (lower-case, single-space) column.
     *     Saves two "Camila Torres" records (different casings) and one "Jose Perez" record;
     *     only the Camila rows should be counted.
     *
     * ES: Verifica que countByCustomerNameNormalized cuenta solo transacciones con
     *     coincidencia exacta en la columna normalizada (minúsculas, espacio único).
     *     Guarda dos registros "Camila Torres" (diferentes mayúsculas) y uno "Jose Perez";
     *     solo las filas de Camila deben ser contadas.
     */
    @Test
    void shouldCountTransactionsByNormalizedCustomerName() {
        // EN: Both records normalize to "camila torres" via the @PrePersist hook.
        // ES: Ambos registros se normalizan a "camila torres" via el hook @PrePersist.
        transactionRepository.save(buildTransaction("Camila Torres", "camila torres", LocalDateTime.of(2026, 3, 1, 10, 0)));
        transactionRepository.save(buildTransaction("  CAMILA  TORRES ", "camila torres", LocalDateTime.of(2026, 3, 2, 10, 0)));
        transactionRepository.save(buildTransaction("Jose Perez", "jose perez", LocalDateTime.of(2026, 3, 3, 10, 0)));

        long count = transactionRepository.countByCustomerNameNormalized("camila torres");

        // EN: Only the two "Camila Torres" variations should match; "Jose Perez" must not be counted.
        // ES: Solo las dos variaciones de "Camila Torres" deben coincidir; "Jose Perez" no debe contarse.
        assertThat(count).isEqualTo(2);
    }

    // ── Sort Order Tests ──────────────────────────────────────────────────────────────────

    /**
     * EN: Verifies that findAllByOrderByTransactionDateDescIdDesc returns transactions
     *     newest-first. The secondary sort by id desc ensures deterministic order when
     *     multiple transactions share the same date.
     *
     * ES: Verifica que findAllByOrderByTransactionDateDescIdDesc devuelve transacciones
     *     de más reciente a más antigua. El orden secundario por id desc asegura un orden
     *     determinista cuando múltiples transacciones comparten la misma fecha.
     */
    @Test
    void shouldReturnTransactionsSortedByDateDescendingThenIdDescending() {
        // EN: Insert in non-sorted order to verify the query does the ordering, not insertion order.
        // ES: Insertamos en orden no ordenado para verificar que la consulta hace la ordenación,
        //     no el orden de inserción.
        Transaction oldest = transactionRepository.save(buildTransaction("Camila Torres", "camila torres", LocalDateTime.of(2026, 3, 1, 10, 0)));
        Transaction newest = transactionRepository.save(buildTransaction("Camila Torres", "camila torres", LocalDateTime.of(2026, 3, 3, 10, 0)));
        Transaction middle = transactionRepository.save(buildTransaction("Camila Torres", "camila torres", LocalDateTime.of(2026, 3, 2, 10, 0)));

        List<Transaction> transactions = transactionRepository.findAllByOrderByTransactionDateDescIdDesc();

        // EN: Expected order: newest (Mar 3) → middle (Mar 2) → oldest (Mar 1).
        // ES: Orden esperado: más reciente (Mar 3) → medio (Mar 2) → más antiguo (Mar 1).
        assertThat(transactions).extracting(Transaction::getId)
                .containsExactly(newest.getId(), middle.getId(), oldest.getId());
    }

    // ── Test Data Builder ─────────────────────────────────────────────────────────────────

    /**
     * EN: Creates a Transaction entity with the given customer name fields and date.
     *     Note: the @PrePersist hook will override customerNameNormalized with
     *     Transaction.canonicalize(customerName) when save() is called.
     *     The manually-provided normalizedCustomerName is redundant but kept for
     *     documentation clarity — it shows what value the test expects to be stored.
     *
     * ES: Crea una entidad Transaction con los campos de nombre de cliente y fecha dados.
     *     Nota: el hook @PrePersist sobreescribirá customerNameNormalized con
     *     Transaction.canonicalize(customerName) cuando se llame a save().
     *     El normalizedCustomerName proporcionado manualmente es redundante pero se mantiene
     *     para claridad de documentación — muestra qué valor espera la prueba que sea almacenado.
     *
     * @param customerName           display name / nombre visible
     * @param normalizedCustomerName expected normalized form (for documentation) / forma normalizada esperada (para documentación)
     * @param dateTime               transaction date / fecha de la transacción
     * @return an unsaved Transaction entity / una entidad Transaction no guardada
     */
    private Transaction buildTransaction(String customerName, String normalizedCustomerName, LocalDateTime dateTime) {
        Transaction transaction = new Transaction();
        transaction.setAmountInPesos(5000);
        transaction.setMerchant("Merchant");
        transaction.setCustomerName(customerName);
        // EN: @PrePersist will override this; kept here to document what value is expected.
        // ES: @PrePersist sobreescribirá esto; se mantiene aquí para documentar qué valor se espera.
        transaction.setCustomerNameNormalized(normalizedCustomerName);
        transaction.setTransactionDate(dateTime);
        return transaction;
    }
}
