package com.tenpo.challenge.transaction;

import org.springframework.stereotype.Component;

/**
 * EN: Mapper component responsible for converting {@link Transaction} JPA entities
 *     into {@link TransactionResponse} DTOs.
 *     Extracting the mapping into a dedicated class follows the Single Responsibility
 *     Principle and keeps both the entity and the service free of DTO-shaping concerns.
 *
 *     This class intentionally omits {@code customerNameNormalized} from the response
 *     DTO — that field is an internal persistence detail and should not be part of
 *     the public API contract.
 *
 * ES: Componente mapper responsable de convertir entidades JPA {@link Transaction}
 *     en DTOs {@link TransactionResponse}.
 *     Extraer el mapeo en una clase dedicada sigue el Principio de Responsabilidad Única
 *     y mantiene tanto la entidad como el servicio libres de preocupaciones de formateo de DTO.
 *
 *     Esta clase omite intencionalmente {@code customerNameNormalized} del DTO de respuesta
 *     — ese campo es un detalle interno de persistencia y no debe ser parte del
 *     contrato público de la API.
 *
 * Design — SOLID:
 *   SRP : Only transforms entities to DTOs; no persistence, no business logic.
 *   OCP : A new field in the response can be added here without modifying the entity.
 */
@Component
public class TransactionMapper {

    /**
     * EN: Converts a {@link Transaction} entity to its public {@link TransactionResponse} representation.
     *     Only the fields intended for public exposure are copied; internal fields
     *     (e.g. {@code customerNameNormalized}) are intentionally excluded.
     *
     * ES: Convierte una entidad {@link Transaction} a su representación pública {@link TransactionResponse}.
     *     Solo se copian los campos destinados a exposición pública; los campos internos
     *     (ej. {@code customerNameNormalized}) se excluyen intencionalmente.
     *
     * @param transaction the source entity to map / la entidad fuente a mapear
     * @return the corresponding response DTO / el DTO de respuesta correspondiente
     */
    public TransactionResponse toResponse(Transaction transaction) {
        // EN: Field-by-field projection — only public API fields are included.
        // ES: Proyección campo por campo — solo se incluyen los campos de la API pública.
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAmountInPesos(),
                transaction.getMerchant(),
                transaction.getCustomerName(),
                transaction.getTransactionDate()
        );
    }
}
