package com.tenpo.challenge.transaction;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * EN: Spring Data JPA repository for {@link Transaction} entities.
 *     Provides standard CRUD operations via {@link JpaRepository} and declares
 *     three derived-query methods that Spring Data resolves at boot time by parsing
 *     the method names — no explicit JPQL or native SQL is needed.
 *
 *     Performance note: both list methods correspond to queries that hit the compound
 *     index {@code idx_transactions_transaction_date} (transaction_date DESC, id DESC)
 *     and the single-column index {@code idx_transactions_customer_name_normalized},
 *     keeping reads efficient even as the table grows.
 *
 * ES: Repositorio Spring Data JPA para entidades {@link Transaction}.
 *     Proporciona operaciones CRUD estándar a través de {@link JpaRepository} y declara
 *     tres métodos de consulta derivados que Spring Data resuelve en el arranque al analizar
 *     los nombres de métodos — no se necesita JPQL explícito ni SQL nativo.
 *
 *     Nota de rendimiento: ambos métodos de lista corresponden a consultas que usan el índice
 *     compuesto {@code idx_transactions_transaction_date} (transaction_date DESC, id DESC)
 *     y el índice de columna única {@code idx_transactions_customer_name_normalized},
 *     manteniendo las lecturas eficientes incluso al crecer la tabla.
 *
 * Design — SOLID:
 *   ISP : Each method has a single, specific purpose; no broad "find all by example" contract.
 *   DIP : Service depends on this interface, not on a concrete implementation.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {

    /**
     * EN: Counts how many transactions are owned by the given normalized customer name.
     *     Used by the service to enforce the 100-transaction-per-customer business rule.
     *     Targets the {@code idx_transactions_customer_name_normalized} index for O(log n) lookup.
     *
     * ES: Cuenta cuántas transacciones pertenecen al nombre de cliente normalizado dado.
     *     Usado por el servicio para hacer cumplir la regla de negocio de 100 transacciones por cliente.
     *     Usa el índice {@code idx_transactions_customer_name_normalized} para búsqueda O(log n).
     *
     * @param customerNameNormalized the canonical lower-cased, single-spaced name
     *                               / el nombre canónico en minúsculas con un solo espacio
     * @return count of matching transactions / conteo de transacciones que coinciden
     */
    long countByCustomerNameNormalized(String customerNameNormalized);

    /**
     * EN: Returns all transactions sorted by transaction date descending, then by id descending.
     *     The secondary sort by id ensures a stable, deterministic order when two transactions
     *     share the exact same timestamp (e.g. test data or bulk inserts).
     *
     * ES: Devuelve todas las transacciones ordenadas por fecha de transacción descendente,
     *     luego por id descendente. El orden secundario por id asegura un orden estable y
     *     determinista cuando dos transacciones comparten exactamente el mismo timestamp
     *     (ej. datos de prueba o inserciones en lote).
     *
     * @return all transactions newest-first / todas las transacciones de más reciente a más antigua
     */
    List<Transaction> findAllByOrderByTransactionDateDescIdDesc();

    /**
     * EN: Returns transactions for a specific Tenpista, sorted newest-first.
     *     Uses the normalized column to perform a case- and space-insensitive equality match.
     *     This is the server-side counterpart to the local customer filter in the React frontend.
     *
     * ES: Devuelve transacciones para un Tenpista específico, ordenadas de más reciente a más antigua.
     *     Usa la columna normalizada para realizar una coincidencia de igualdad insensible a mayúsculas y espacios.
     *     Este es el contraparte del lado del servidor al filtro de cliente local en el frontend de React.
     *
     * @param customerNameNormalized the canonical name key / la clave de nombre canónico
     * @return transactions for that Tenpista / transacciones para ese Tenpista
     */
    List<Transaction> findAllByCustomerNameNormalizedOrderByTransactionDateDescIdDesc(String customerNameNormalized);
}
