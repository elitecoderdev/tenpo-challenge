package com.tenpo.challenge.transaction;

import com.tenpo.challenge.shared.exception.BusinessRuleException;
import com.tenpo.challenge.shared.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * EN: Application service that owns all business logic for Tenpista transactions.
 *     This class is the single source of truth for rules such as:
 *       - the 100-transaction-per-customer cap
 *       - input sanitization (whitespace normalization)
 *       - ID resolution with a consistent "not found" error
 *
 *     The service delegates persistence to {@link TransactionRepository} and
 *     response shaping to {@link TransactionMapper}, keeping itself focused on
 *     orchestration and domain rules (Single Responsibility Principle).
 *
 * ES: Servicio de aplicación que posee toda la lógica de negocio para transacciones de Tenpista.
 *     Esta clase es la única fuente de verdad para reglas como:
 *       - el límite de 100 transacciones por cliente
 *       - saneamiento de entrada (normalización de espacios)
 *       - resolución de IDs con un error consistente de "no encontrado"
 *
 *     El servicio delega la persistencia a {@link TransactionRepository} y
 *     el formateo de respuesta a {@link TransactionMapper}, manteniéndose enfocado en
 *     orquestación y reglas de dominio (Principio de Responsabilidad Única).
 *
 * Design — SOLID:
 *   SRP : Business rules and orchestration only; no HTTP or persistence details.
 *   OCP : New business rules can be added without modifying existing public methods.
 *   DIP : Depends on repository and mapper abstractions via constructor injection.
 *
 * Performance note:
 *   Read operations use {@code readOnly = true} on the @Transactional annotation,
 *   which allows the JPA provider to skip dirty-checking and may improve throughput.
 */
@Service
public class TransactionService {

    // EN: Business rule constant — a single Tenpista may not exceed 100 transactions.
    //     Stored as a named constant to avoid magic numbers and make the intent clear.
    // ES: Constante de regla de negocio — un solo Tenpista no puede superar 100 transacciones.
    //     Almacenada como constante con nombre para evitar números mágicos y hacer clara la intención.
    private static final int MAX_TRANSACTIONS_PER_CUSTOMER = 100;

    // EN: Collaborators injected via constructor (Dependency Inversion Principle).
    //     Using final fields guarantees immutability after construction.
    // ES: Colaboradores inyectados via constructor (Principio de Inversión de Dependencias).
    //     Usar campos final garantiza inmutabilidad después de la construcción.
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionRepository transactionRepository, TransactionMapper transactionMapper) {
        this.transactionRepository = transactionRepository;
        this.transactionMapper = transactionMapper;
    }

    // ── Queries ───────────────────────────────────────────────────────────────────────────

    /**
     * EN: Returns all transactions sorted by date descending, then id descending.
     *     If {@code customerName} is provided and non-blank, the result is scoped to
     *     that Tenpista using the normalized name column (case- and space-insensitive).
     *     {@code readOnly = true} skips dirty-checking for better performance.
     *
     * ES: Devuelve todas las transacciones ordenadas por fecha descendente, luego id descendente.
     *     Si {@code customerName} se proporciona y no está en blanco, el resultado se limita a
     *     ese Tenpista usando la columna de nombre normalizado (insensible a mayúsculas y espacios).
     *     {@code readOnly = true} omite la verificación de cambios para mejor rendimiento.
     *
     * @param customerName optional filter / filtro opcional
     * @return ordered list of transaction responses / lista ordenada de respuestas de transacción
     */
    @Transactional(readOnly = true)
    public List<TransactionResponse> listTransactions(String customerName) {
        // EN: Branch between filtered and unfiltered repository queries based on whether
        //     a non-blank customerName filter was supplied by the caller.
        // ES: Rama entre consultas de repositorio filtradas y no filtradas según si el llamador
        //     proporcionó un filtro de customerName no vacío.
        List<Transaction> transactions = hasText(customerName)
                ? transactionRepository.findAllByCustomerNameNormalizedOrderByTransactionDateDescIdDesc(
                        Transaction.canonicalize(customerName))
                : transactionRepository.findAllByOrderByTransactionDateDescIdDesc();

        // EN: Map every entity to a response DTO. Using Stream.toList() produces an unmodifiable list.
        // ES: Mapeamos cada entidad a un DTO de respuesta. Stream.toList() produce una lista no modificable.
        return transactions.stream().map(transactionMapper::toResponse).toList();
    }

    /**
     * EN: Retrieves a single transaction by its id, throwing {@link ResourceNotFoundException}
     *     (mapped to HTTP 404) if the id does not exist.
     *
     * ES: Recupera una sola transacción por su id, lanzando {@link ResourceNotFoundException}
     *     (mapeado a HTTP 404) si el id no existe.
     *
     * @param transactionId the primary key / la clave primaria
     * @return the found transaction response / la respuesta de transacción encontrada
     */
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Integer transactionId) {
        return transactionMapper.toResponse(findTransaction(transactionId));
    }

    // ── Commands ──────────────────────────────────────────────────────────────────────────

    /**
     * EN: Creates and persists a new transaction for the Tenpista identified in the request.
     *     Enforces the 100-transaction quota before persisting.
     *     Sanitizes all text fields (trim + collapse whitespace) before saving.
     *
     * ES: Crea y persiste una nueva transacción para el Tenpista identificado en la solicitud.
     *     Hace cumplir la cuota de 100 transacciones antes de persistir.
     *     Sanitiza todos los campos de texto (recortar + colapsar espacios) antes de guardar.
     *
     * @param request the validated incoming payload / el payload entrante validado
     * @return the persisted transaction response / la respuesta de transacción persistida
     */
    @Transactional
    public TransactionResponse createTransaction(TransactionRequest request) {
        // EN: Normalize the customer name so the quota check uses the same canonical form
        //     as the database index.
        // ES: Normalizamos el nombre del cliente para que la verificación de cuota use la misma
        //     forma canónica que el índice de la base de datos.
        String sanitizedCustomerName = sanitizeText(request.customerName());

        // EN: Verify the customer has not reached the 100-transaction limit.
        //     Passing null as the existing transaction signals this is a new record.
        // ES: Verificamos que el cliente no haya alcanzado el límite de 100 transacciones.
        //     Pasar null como transacción existente señala que este es un nuevo registro.
        ensureTransactionQuota(sanitizedCustomerName, null);

        // EN: Build a blank entity and populate it from the request.
        // ES: Construimos una entidad en blanco y la llenamos desde la solicitud.
        Transaction transaction = new Transaction();
        applyRequest(transaction, request, sanitizedCustomerName);

        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    /**
     * EN: Fully replaces the fields of an existing transaction.
     *     Re-checks the quota if the customer name changes to a different Tenpista.
     *     Returns the updated state.
     *
     * ES: Reemplaza completamente los campos de una transacción existente.
     *     Re-verifica la cuota si el nombre del cliente cambia a un Tenpista diferente.
     *     Devuelve el estado actualizado.
     *
     * @param transactionId the id of the record to update / el id del registro a actualizar
     * @param request       the updated payload / el payload actualizado
     * @return the updated transaction response / la respuesta de transacción actualizada
     */
    @Transactional
    public TransactionResponse updateTransaction(Integer transactionId, TransactionRequest request) {
        // EN: Load the existing record first, or fail fast with 404 if it does not exist.
        // ES: Cargamos el registro existente primero, o fallamos rápido con 404 si no existe.
        Transaction transaction = findTransaction(transactionId);
        String sanitizedCustomerName = sanitizeText(request.customerName());

        // EN: Only re-check the quota if the update changes the owning Tenpista.
        //     Same customer → no quota change.
        // ES: Solo re-verificamos la cuota si la actualización cambia el Tenpista propietario.
        //     Mismo cliente → sin cambio de cuota.
        ensureTransactionQuota(sanitizedCustomerName, transaction);

        applyRequest(transaction, request, sanitizedCustomerName);
        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    /**
     * EN: Deletes a transaction by id. Throws {@link ResourceNotFoundException} if not found.
     *
     * ES: Elimina una transacción por id. Lanza {@link ResourceNotFoundException} si no se encuentra.
     *
     * @param transactionId the id of the record to delete / el id del registro a eliminar
     */
    @Transactional
    public void deleteTransaction(Integer transactionId) {
        // EN: Resolve first to produce a meaningful 404 rather than silently succeeding.
        // ES: Resolvemos primero para producir un 404 significativo en lugar de tener éxito silenciosamente.
        Transaction transaction = findTransaction(transactionId);
        transactionRepository.delete(transaction);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────────────────

    /**
     * EN: Resolves a transaction by its id or throws a domain exception that maps to HTTP 404.
     *     Centralizes the "not found" pattern so all public methods produce a consistent error.
     *
     * ES: Resuelve una transacción por su id o lanza una excepción de dominio que mapea a HTTP 404.
     *     Centraliza el patrón "no encontrado" para que todos los métodos públicos produzcan un error consistente.
     *
     * @param transactionId the primary key to look up / la clave primaria a buscar
     * @return the found entity / la entidad encontrada
     */
    private Transaction findTransaction(Integer transactionId) {
        return transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Transaction with id " + transactionId + " was not found."));
    }

    /**
     * EN: Copies all mutable fields from the incoming request onto the entity.
     *     This private helper is called by both createTransaction and updateTransaction
     *     to avoid code duplication (DRY principle).
     *
     * ES: Copia todos los campos mutables de la solicitud entrante a la entidad.
     *     Este helper privado es llamado tanto por createTransaction como updateTransaction
     *     para evitar duplicación de código (principio DRY).
     *
     * @param transaction           the entity to mutate / la entidad a mutar
     * @param request               the source payload / el payload fuente
     * @param sanitizedCustomerName pre-sanitized customer name / nombre de cliente pre-sanitizado
     */
    private void applyRequest(Transaction transaction, TransactionRequest request, String sanitizedCustomerName) {
        // EN: Math.toIntExact ensures an ArithmeticException if the long value overflows int,
        //     providing an extra safety layer beyond the @Max validation on the request DTO.
        // ES: Math.toIntExact asegura una ArithmeticException si el valor long desborda int,
        //     proporcionando una capa de seguridad adicional más allá de la validación @Max en el DTO.
        transaction.setAmountInPesos(Math.toIntExact(request.amountInPesos()));
        transaction.setMerchant(sanitizeText(request.merchant()));
        transaction.setCustomerName(sanitizedCustomerName);
        transaction.setTransactionDate(request.transactionDate());
    }

    /**
     * EN: Enforces the 100-transaction-per-customer business rule.
     *     The check is skipped when the customer name has not changed (update scenario)
     *     to avoid an unnecessary database count query.
     *     Throws {@link BusinessRuleException} (mapped to HTTP 409 Conflict) on violation.
     *
     * ES: Hace cumplir la regla de negocio de 100 transacciones por cliente.
     *     La verificación se omite cuando el nombre del cliente no ha cambiado (escenario de actualización)
     *     para evitar una consulta de conteo innecesaria a la base de datos.
     *     Lanza {@link BusinessRuleException} (mapeado a HTTP 409 Conflict) en caso de violación.
     *
     * @param customerName        the sanitized (but not yet canonicalized) customer name
     *                            / el nombre de cliente sanitizado (pero aún no canonizado)
     * @param existingTransaction null on create; the existing entity on update
     *                            / null en creación; la entidad existente en actualización
     */
    private void ensureTransactionQuota(String customerName, Transaction existingTransaction) {
        String normalizedCustomerName = Transaction.canonicalize(customerName);

        // EN: Skip the count query when the customer name has not changed during an update.
        //     An update to the same customer cannot change the total count.
        // ES: Omitimos la consulta de conteo cuando el nombre del cliente no ha cambiado durante una actualización.
        //     Una actualización del mismo cliente no puede cambiar el conteo total.
        boolean customerChanged = existingTransaction == null
                || !normalizedCustomerName.equals(existingTransaction.getCustomerNameNormalized());

        if (!customerChanged) {
            return;
        }

        // EN: Count existing transactions for this canonical customer name using the indexed column.
        // ES: Contamos las transacciones existentes para este nombre de cliente canónico usando la columna indexada.
        long customerTransactions = transactionRepository.countByCustomerNameNormalized(normalizedCustomerName);
        if (customerTransactions >= MAX_TRANSACTIONS_PER_CUSTOMER) {
            throw new BusinessRuleException(
                    "A Tenpista can only have up to 100 transactions.",
                    HttpStatus.CONFLICT
            );
        }
    }

    /**
     * EN: Normalizes whitespace before persisting so search, validation and UI stay predictable.
     *     Trims leading/trailing whitespace and collapses any internal run of spaces to one.
     *     This is intentionally less aggressive than {@link Transaction#canonicalize(String)} —
     *     it preserves the original casing so the display name looks natural in the UI.
     *
     * ES: Normaliza los espacios antes de persistir para que la búsqueda, validación y UI sean predecibles.
     *     Recorta espacios al inicio/final y colapsa cualquier secuencia de espacios internos a uno.
     *     Es intencionalmente menos agresivo que {@link Transaction#canonicalize(String)} —
     *     preserva las mayúsculas originales para que el nombre de visualización se vea natural en la UI.
     *
     * @param value the raw string to sanitize / la cadena sin procesar a sanitizar
     * @return the sanitized string / la cadena sanitizada
     */
    // EN: Normalize whitespace before persisting so search, validation and UI stay predictable.
    // ES: Normalizamos los espacios antes de persistir para que la búsqueda, la validación y la UI sean predecibles.
    private String sanitizeText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    /**
     * EN: Returns {@code true} if the given string is non-null and contains at least one
     *     non-whitespace character. Used to decide whether to apply a filter parameter.
     *
     * ES: Devuelve {@code true} si la cadena dada no es null y contiene al menos un
     *     carácter que no sea espacio. Se usa para decidir si aplicar un parámetro de filtro.
     *
     * @param value the string to check / la cadena a verificar
     * @return true if the string has meaningful content / true si la cadena tiene contenido significativo
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
