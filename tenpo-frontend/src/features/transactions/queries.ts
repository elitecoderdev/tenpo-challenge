import { keepPreviousData } from '@tanstack/react-query'
import { apiClient } from '../../app/api'
import type { Transaction, TransactionPayload } from './types'

/**
 * EN: API query and mutation helpers for the transactions feature.
 *     These functions are the single source of truth for how the frontend communicates
 *     with the backend /api/transactions endpoints.
 *     They are intentionally plain async functions (not hooks) so they can be used
 *     both inside useMutation callbacks and standalone in tests.
 *
 * ES: Helpers de consulta y mutación de API para la característica de transacciones.
 *     Estas funciones son la única fuente de verdad para cómo el frontend se comunica
 *     con los endpoints /api/transactions del backend.
 *     Son intencionalmente funciones async simples (no hooks) para que puedan usarse
 *     tanto dentro de los callbacks de useMutation como de forma independiente en tests.
 */

// ── Query Key Factory ─────────────────────────────────────────────────────────────────────

/**
 * EN: Centralized React Query key factory for the transactions cache.
 *     Using a factory object (instead of raw strings) makes cache invalidation
 *     predictable: every module that needs to read or write the transactions cache
 *     imports this object rather than repeating the string literal.
 *     The 'as const' assertion narrows the type to a readonly tuple, which improves
 *     TypeScript inference in useQuery and useQueryClient.setQueryData calls.
 *
 * ES: Fábrica de claves React Query centralizada para el cache de transacciones.
 *     Usar un objeto de fábrica (en lugar de cadenas crudas) hace que la invalidación
 *     del cache sea predecible: cada módulo que necesite leer o escribir el cache de
 *     transacciones importa este objeto en lugar de repetir el literal de cadena.
 *     La aserción 'as const' estrecha el tipo a una tupla de solo lectura, lo que mejora
 *     la inferencia de TypeScript en las llamadas useQuery y useQueryClient.setQueryData.
 */
export const transactionKeys = {
  all: ['transactions'] as const,
}

// ── Query Options ─────────────────────────────────────────────────────────────────────────

/**
 * EN: Query options object passed to useQuery in App.tsx.
 *     Defined here (not inline in the component) so the same options can be reused
 *     in tests or other components without duplication (DRY principle).
 *
 *     keepPreviousData: while a refetch is in progress, the UI shows the last successful
 *     data instead of the loading skeleton. This prevents layout shifts during background
 *     revalidation and makes the manual refresh button feel snappier.
 *
 * ES: Objeto de opciones de consulta pasado a useQuery en App.tsx.
 *     Definido aquí (no en línea en el componente) para que las mismas opciones puedan
 *     reutilizarse en tests u otros componentes sin duplicación (principio DRY).
 *
 *     keepPreviousData: mientras un refetch está en progreso, la UI muestra los últimos
 *     datos exitosos en lugar del esqueleto de carga. Esto previene desplazamientos de
 *     layout durante la revalidación en segundo plano y hace que el botón de actualización
 *     manual se sienta más ágil.
 */
export const transactionsQueryOptions = {
  queryKey: transactionKeys.all,
  queryFn: async (): Promise<Transaction[]> => {
    // EN: Fetch the full list ordered by date desc (server-side sort).
    //     The response is typed so TypeScript can catch shape mismatches at compile time.
    // ES: Obtenemos la lista completa ordenada por fecha desc (ordenación del lado del servidor).
    //     La respuesta está tipada para que TypeScript pueda detectar inconsistencias de forma en tiempo de compilación.
    const response = await apiClient.get<Transaction[]>('/transactions')
    return response.data
  },
  // EN: Show previous data while revalidating to prevent loading flicker.
  // ES: Mostramos datos anteriores mientras se revalida para prevenir parpadeo de carga.
  placeholderData: keepPreviousData,
}

// ── Mutation Helpers ──────────────────────────────────────────────────────────────────────

/**
 * EN: Creates a new transaction by sending a POST request.
 *     Returns the created transaction with its server-assigned id so the caller
 *     can update the local cache without a follow-up GET request.
 *
 * ES: Crea una nueva transacción enviando una solicitud POST.
 *     Devuelve la transacción creada con su id asignado por el servidor para que el llamador
 *     pueda actualizar el cache local sin una solicitud GET de seguimiento.
 *
 * @param payload - the transaction data to create / los datos de transacción a crear
 * @returns the persisted transaction with its id / la transacción persistida con su id
 */
export const createTransaction = async (
  payload: TransactionPayload,
): Promise<Transaction> => {
  const response = await apiClient.post<Transaction>('/transactions', payload)
  return response.data
}

/**
 * EN: Updates an existing transaction by sending a PUT request (full replacement semantics).
 *     Returns the updated state so the caller can sync the local cache.
 *
 * ES: Actualiza una transacción existente enviando una solicitud PUT (semántica de reemplazo completo).
 *     Devuelve el estado actualizado para que el llamador pueda sincronizar el cache local.
 *
 * @param transactionId - the id of the transaction to update / el id de la transacción a actualizar
 * @param payload       - the updated data / los datos actualizados
 * @returns the updated transaction / la transacción actualizada
 */
export const updateTransaction = async (
  transactionId: number,
  payload: TransactionPayload,
): Promise<Transaction> => {
  const response = await apiClient.put<Transaction>(
    `/transactions/${transactionId}`,
    payload,
  )
  return response.data
}

/**
 * EN: Deletes a transaction by id. Returns void on success.
 *     The caller is responsible for removing the entry from the local cache.
 *
 * ES: Elimina una transacción por id. Devuelve void en caso de éxito.
 *     El llamador es responsable de eliminar la entrada del cache local.
 *
 * @param transactionId - the id of the transaction to delete / el id de la transacción a eliminar
 */
export const deleteTransaction = async (transactionId: number): Promise<void> => {
  await apiClient.delete(`/transactions/${transactionId}`)
}
