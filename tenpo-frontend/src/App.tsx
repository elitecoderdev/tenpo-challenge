/**
 * EN: Root application component — orchestrates all application state and
 *     connects the API layer to the UI layer.
 *     Responsibilities:
 *       1. Fetch the transaction list once and keep it in the React Query cache.
 *       2. Manage local UI state: active transaction, modals, filter, toasts.
 *       3. Handle create / update / delete mutations with optimistic cache updates.
 *       4. Coordinate the side-panel editor and the create modal.
 *
 * ES: Componente raíz de la aplicación — orquesta todo el estado de la aplicación
 *     y conecta la capa de API con la capa de UI.
 *     Responsabilidades:
 *       1. Obtener la lista de transacciones una vez y mantenerla en el cache de React Query.
 *       2. Gestionar el estado de UI local: transacción activa, modales, filtro, toasts.
 *       3. Manejar las mutaciones de crear/actualizar/eliminar con actualizaciones optimistas del cache.
 *       4. Coordinar el editor del panel lateral y el modal de creación.
 *
 * Design — SOLID:
 *   SRP : App is the composition root; domain logic lives in queries.ts / TransactionForm.
 *   OCP : New mutations can be added as independent useMutation hooks.
 *   DIP : Depends on abstract query/mutation helpers, not on Axios directly.
 */

import { useDeferredValue, useEffect, useRef, useState, useTransition } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import dayjs from 'dayjs'
import './App.css'
import {
  createTransaction,
  deleteTransaction,
  transactionKeys,
  transactionsQueryOptions,
  updateTransaction,
} from './features/transactions/queries'
import { Modal } from './components/Modal'
import { TransactionForm } from './features/transactions/TransactionForm'
import { TransactionList } from './features/transactions/TransactionList'
import type { ApiError, Transaction, TransactionPayload } from './features/transactions/types'
// EN: Import the shared CLP formatter (DRY fix — previously duplicated here and in TransactionList).
// ES: Importamos el formateador CLP compartido (corrección DRY — antes duplicado aquí y en TransactionList).
import { formatCLP } from './lib/formatters'

// ── Error Normalisation Helpers ────────────────────────────────────────────────────────────

/**
 * EN: Normalize a raw backend error payload into a guaranteed-shape ApiError object.
 *     Handles three cases:
 *       1. Well-formed ApiError JSON from the backend — fields are used as-is.
 *       2. Malformed/partial JSON object — missing fields are replaced with safe defaults.
 *       3. Non-object payload (e.g. plain string or null) — a synthetic ApiError is created.
 *     This function is the single bridge between the "unknown" axios error shape and
 *     the typed ApiError used throughout the UI.
 *
 * ES: Normalizamos un payload de error crudo del backend a un objeto ApiError con forma garantizada.
 *     Maneja tres casos:
 *       1. JSON ApiError bien formado del backend — los campos se usan tal cual.
 *       2. Objeto JSON malformado/parcial — los campos faltantes se reemplazan con valores predeterminados seguros.
 *       3. Payload no-objeto (ej. string plano o null) — se crea un ApiError sintético.
 *     Esta función es el puente único entre la forma "desconocida" del error de axios y
 *     el ApiError tipado usado en toda la UI.
 *
 * @param status   - HTTP status code from the response / código de estado HTTP de la respuesta
 * @param statusText - HTTP status text (e.g. "Bad Request") / texto de estado HTTP
 * @param data     - raw response body / cuerpo crudo de la respuesta
 * @returns a fully-shaped ApiError / un ApiError completamente formado
 */
const normalizeApiError = (
  status: number | undefined,
  statusText: string | undefined,
  data: unknown,
): ApiError => {
  // EN: Build a fallback human-readable message from whatever the server sent.
  //     Prefer the raw body string over a hard-coded default.
  // ES: Construimos un mensaje de reserva legible por humanos de lo que el servidor envió.
  //     Preferimos el string del cuerpo crudo sobre un predeterminado fijo.
  const fallbackMessage =
    typeof data === 'string' && data.trim()
      ? data.trim()
      : 'The server returned an unexpected response.'

  // EN: If the response body is an object (but not an array), attempt field-by-field coercion
  //     to tolerantly handle partially-valid ApiError shapes.
  // ES: Si el cuerpo de la respuesta es un objeto (pero no un array), intentamos la conversión
  //     campo por campo para manejar con tolerancia formas de ApiError parcialmente válidas.
  if (data && typeof data === 'object' && !Array.isArray(data)) {
    const candidate = data as Partial<ApiError>

    return {
      // EN: Use candidate's timestamp if it's a valid string; otherwise default to now.
      // ES: Usamos el timestamp del candidato si es un string válido; de lo contrario defaulteamos a ahora.
      timestamp:
        typeof candidate.timestamp === 'string'
          ? candidate.timestamp
          : new Date().toISOString(),
      status: typeof candidate.status === 'number' ? candidate.status : (status ?? 500),
      error:
        typeof candidate.error === 'string'
          ? candidate.error
          : (statusText?.trim() || 'Request failed'),
      message:
        typeof candidate.message === 'string' ? candidate.message : fallbackMessage,
      path: typeof candidate.path === 'string' ? candidate.path : '',
      // EN: fieldErrors must be an array — guard against the server sending a non-array.
      // ES: fieldErrors debe ser un array — protegemos contra que el servidor envíe un no-array.
      fieldErrors: Array.isArray(candidate.fieldErrors) ? candidate.fieldErrors : [],
    }
  }

  // EN: Fallback: server sent something completely unexpected (empty body, HTML, etc.).
  // ES: Reserva: el servidor envió algo completamente inesperado (cuerpo vacío, HTML, etc.).
  return {
    timestamp: new Date().toISOString(),
    status: status ?? 500,
    error: statusText?.trim() || 'Request failed',
    message: fallbackMessage,
    path: '',
    fieldErrors: [],
  }
}

/**
 * EN: Extracts an ApiError from an unknown thrown value, or returns null if the
 *     error is not an Axios HTTP error.
 *     Using axios.isAxiosError as a type guard means TypeScript narrows the type
 *     to AxiosError<ApiError> inside the if-block, making response.data safe to access.
 *
 * ES: Extraemos un ApiError de un valor lanzado desconocido, o devolvemos null si el
 *     error no es un error HTTP de Axios.
 *     Usar axios.isAxiosError como type guard significa que TypeScript estrecha el tipo
 *     a AxiosError<ApiError> dentro del bloque if, haciendo seguro el acceso a response.data.
 *
 * @param error - any thrown value / cualquier valor lanzado
 * @returns ApiError if the error came from an HTTP response, null otherwise
 *          / ApiError si el error vino de una respuesta HTTP, null de lo contrario
 */
const extractApiError = (error: unknown): ApiError | null => {
  if (!axios.isAxiosError<ApiError>(error)) {
    return null
  }

  return normalizeApiError(
    error.response?.status,
    error.response?.statusText ?? error.message,
    error.response?.data,
  )
}

// ── List Sorting ───────────────────────────────────────────────────────────────────────────

/**
 * EN: Sort a transaction array by date descending, then by id descending as a tiebreaker.
 *     Returns a NEW array (does not mutate the cache reference) so React Query's
 *     referential equality check can correctly detect when data has changed.
 *     This mirrors the ORDER BY in TransactionRepository so the client view stays
 *     consistent with fresh server data.
 *
 * ES: Ordenamos un array de transacciones por fecha descendente, luego por id descendente
 *     como desempate. Devuelve un NUEVO array (no muta la referencia del cache) para que
 *     la verificación de igualdad referencial de React Query pueda detectar correctamente
 *     cuándo los datos han cambiado.
 *     Esto refleja el ORDER BY en TransactionRepository para que la vista del cliente
 *     permanezca consistente con datos frescos del servidor.
 *
 * @param transactions - array to sort / array a ordenar
 * @returns a new sorted array / un nuevo array ordenado
 */
const sortTransactions = (transactions: Transaction[]) =>
  [...transactions].sort((left, right) => {
    // EN: Primary sort: newest date first. dayjs.valueOf() returns Unix ms for reliable numeric comparison.
    // ES: Ordenamiento primario: fecha más reciente primero. dayjs.valueOf() devuelve ms Unix para comparación numérica confiable.
    const byDate =
      dayjs(right.transactionDate).valueOf() - dayjs(left.transactionDate).valueOf()

    // EN: Tiebreaker: higher id first (insertion order proxy when dates match to the minute).
    // ES: Desempate: id mayor primero (proxy de orden de inserción cuando las fechas coinciden al minuto).
    return byDate !== 0 ? byDate : right.id - left.id
  })

// ── App Component ──────────────────────────────────────────────────────────────────────────

function App() {
  // EN: React Query client — used to manually update the cache after mutations.
  // ES: Cliente de React Query — usado para actualizar manualmente el cache después de las mutaciones.
  const queryClient = useQueryClient()

  // EN: draftCustomerFilter holds the raw input value (updated synchronously on every keystroke).
  //     deferredCustomerFilter is a lower-priority copy that React schedules in the background,
  //     so the input stays responsive while the list re-renders.
  // ES: draftCustomerFilter tiene el valor crudo del input (actualizado sincrónicamente en cada tecla).
  //     deferredCustomerFilter es una copia de menor prioridad que React programa en segundo plano,
  //     para que el input permanezca responsivo mientras la lista se re-renderiza.
  const [draftCustomerFilter, setDraftCustomerFilter] = useState('')

  // EN: activeTransaction — the transaction currently open in the side-panel editor, or null when closed.
  // ES: activeTransaction — la transacción actualmente abierta en el editor del panel lateral, o null cuando está cerrado.
  const [activeTransaction, setActiveTransaction] = useState<Transaction | null>(null)

  // EN: isCreateModalOpen — controls the create-transaction modal visibility.
  // ES: isCreateModalOpen — controla la visibilidad del modal de creación de transacciones.
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)

  // EN: serverError — holds the last API error from a failed mutation for display in the form.
  //     Cleared on every new mutation attempt.
  // ES: serverError — contiene el último error de API de una mutación fallida para mostrar en el formulario.
  //     Se limpia en cada nuevo intento de mutación.
  const [serverError, setServerError] = useState<ApiError | null>(null)

  // EN: useTransition defers side-panel state updates (setActiveTransaction, setIsCreateModalOpen)
  //     so they do not block the urgent UI updates (e.g. button press feedback).
  //     isPanelPending is true while React is processing the deferred state batch.
  // ES: useTransition difiere las actualizaciones de estado del panel lateral para que no bloqueen
  //     las actualizaciones urgentes de la UI (ej. retroalimentación al presionar un botón).
  //     isPanelPending es true mientras React procesa el lote de estado diferido.
  const [isPanelPending, startTransition] = useTransition()

  // EN: toast — ephemeral success message shown after a mutation completes, auto-dismissed after 3 s.
  // ES: toast — mensaje de éxito efímero mostrado después de que una mutación completa, auto-ocultado después de 3 s.
  const [toast, setToast] = useState<string | null>(null)

  // EN: toastTimer ref stores the active setTimeout handle so we can cancel it if
  //     a second toast fires before the first has expired.
  // ES: La ref toastTimer almacena el handle activo de setTimeout para poder cancelarlo si
  //     un segundo toast dispara antes de que el primero haya expirado.
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  // EN: useDeferredValue keeps the filter in sync with the draft, but at a lower render priority.
  //     The filter computation and list re-render use the deferred value so they yield to
  //     higher-priority updates (e.g. typing in the input).
  // ES: useDeferredValue mantiene el filtro sincronizado con el borrador, pero con menor prioridad de renderizado.
  //     El cálculo del filtro y el re-renderizado de la lista usan el valor diferido para ceder a
  //     actualizaciones de mayor prioridad (ej. escribir en el input).
  const deferredCustomerFilter = useDeferredValue(draftCustomerFilter)

  // ── Data Fetching ────────────────────────────────────────────────────────────────────────

  // EN: Fetch the full transaction list once. React Query caches the result for staleTime (1 min)
  //     so page navigations and focus events do not re-fetch (rate limit protection).
  //     transactionsQueryOptions contains queryKey and queryFn, defined in queries.ts.
  // ES: Obtenemos la lista completa de transacciones una vez. React Query almacena el resultado
  //     en caché durante staleTime (1 min) para que las navegaciones de página y eventos de foco
  //     no vuelvan a obtener datos (protección del límite de tasa).
  //     transactionsQueryOptions contiene queryKey y queryFn, definidos en queries.ts.
  const { data: transactions = [], error, isFetching, isLoading, refetch } = useQuery(
    transactionsQueryOptions,
  )

  // ── Derived State ────────────────────────────────────────────────────────────────────────

  // EN: Apply the deferred customer name filter. Uses toLowerCase on both sides for
  //     case-insensitive matching. All filtering is client-side — no extra API requests.
  // ES: Aplicamos el filtro de nombre de cliente diferido. Usa toLowerCase en ambos lados para
  //     coincidencia insensible a mayúsculas. Todo el filtrado es del lado del cliente — sin solicitudes API adicionales.
  const visibleTransactions = transactions.filter((transaction) =>
    transaction.customerName
      .toLowerCase()
      .includes(deferredCustomerFilter.trim().toLowerCase()),
  )

  // EN: Aggregate stats computed from the currently visible (filtered) transaction set.
  //     These update instantly when the filter changes because they are derived values.
  // ES: Estadísticas agregadas calculadas del conjunto de transacciones visibles (filtrado) actualmente.
  //     Estas se actualizan instantáneamente cuando el filtro cambia porque son valores derivados.
  const totalAmount = visibleTransactions.reduce(
    (sum, transaction) => sum + transaction.amountInPesos,
    0,
  )

  // EN: Count unique customers by normalizing names to lowercase before inserting into a Set.
  //     This prevents "Camila Torres" and "camila torres" from counting as two different customers.
  // ES: Contamos clientes únicos normalizando los nombres a minúsculas antes de insertar en un Set.
  //     Esto previene que "Camila Torres" y "camila torres" cuenten como dos clientes diferentes.
  const uniqueCustomers = new Set(
    visibleTransactions.map((transaction) => transaction.customerName.toLowerCase()),
  ).size

  // EN: The first transaction in the visible list is the most recent one (list is sorted desc by date).
  // ES: La primera transacción en la lista visible es la más reciente (la lista está ordenada desc por fecha).
  const latestTransaction = visibleTransactions[0]

  // ── Toast Helper ─────────────────────────────────────────────────────────────────────────

  /**
   * EN: Show a toast notification that auto-dismisses after 3 seconds.
   *     Cancels any previous timer so rapid mutations don't stack toasts.
   *
   * ES: Mostramos una notificación toast que se oculta automáticamente después de 3 segundos.
   *     Cancela cualquier temporizador anterior para que las mutaciones rápidas no apilen toasts.
   *
   * @param message - the message to display / el mensaje a mostrar
   */
  const showToast = (message: string) => {
    if (toastTimer.current) clearTimeout(toastTimer.current)
    setToast(message)
    toastTimer.current = setTimeout(() => setToast(null), 3000)
  }

  // EN: Cleanup the toast timer when App unmounts to prevent a setState call on an
  //     unmounted component (would cause a React warning in dev mode).
  // ES: Limpiamos el temporizador del toast cuando App se desmonta para prevenir una llamada
  //     setState en un componente desmontado (causaría una advertencia de React en modo dev).
  useEffect(() => {
    return () => {
      if (toastTimer.current) clearTimeout(toastTimer.current)
    }
  }, [])

  // ── Mutations ────────────────────────────────────────────────────────────────────────────

  /**
   * EN: Create mutation — calls POST /api/transactions.
   *     On success: inserts the new record into the cache and opens the side-panel editor
   *                 for the newly created transaction (no extra refetch needed).
   *     On error: stores the ApiError so TransactionForm can display field-level errors inline.
   *
   * ES: Mutación de creación — llama a POST /api/transactions.
   *     En éxito: inserta el nuevo registro en el cache y abre el editor del panel lateral
   *               para la transacción recién creada (no se necesita refetch adicional).
   *     En error: almacena el ApiError para que TransactionForm pueda mostrar errores a nivel de campo inline.
   */
  const createMutation = useMutation({
    mutationFn: createTransaction,
    onSuccess: (createdTransaction) => {
      setServerError(null)
      // EN: Eagerly update the cache so we don't burn the 3 req/min quota on an extra refetch.
      //     sortTransactions produces a new array reference, satisfying React Query's change detection.
      // ES: Actualizamos el cache de inmediato para no gastar el limite de 3 req/min en un refetch adicional.
      //     sortTransactions produce una nueva referencia de array, satisfaciendo la detección de cambios de React Query.
      queryClient.setQueryData<Transaction[]>(
        transactionKeys.all,
        (currentTransactions = []) =>
          sortTransactions([createdTransaction, ...currentTransactions]),
      )
      // EN: Wrap state updates in startTransition so they yield to urgent renders
      //     (e.g. the submit button re-enabling immediately after isPending → false).
      // ES: Envolvemos las actualizaciones de estado en startTransition para que cedan a renders urgentes
      //     (ej. el botón de envío re-habilitándose inmediatamente después de isPending → false).
      startTransition(() => {
        setActiveTransaction(createdTransaction)
        setIsCreateModalOpen(false)
      })
      showToast('Transaction created successfully')
    },
    onError: (mutationError) => {
      setServerError(extractApiError(mutationError))
    },
  })

  /**
   * EN: Update mutation — calls PUT /api/transactions/:id.
   *     The payload is passed as an inline object so useMutation receives a single argument.
   *     On success: replaces the updated record in the cache in-place, then refreshes
   *                 activeTransaction so the side panel shows the latest values.
   *
   * ES: Mutación de actualización — llama a PUT /api/transactions/:id.
   *     El payload se pasa como un objeto inline para que useMutation reciba un solo argumento.
   *     En éxito: reemplaza el registro actualizado en el cache en lugar, luego actualiza
   *               activeTransaction para que el panel lateral muestre los valores más recientes.
   */
  const updateMutation = useMutation({
    mutationFn: ({
      transactionId,
      payload,
    }: {
      transactionId: number
      payload: TransactionPayload
    }) => updateTransaction(transactionId, payload),
    onSuccess: (updatedTransaction) => {
      setServerError(null)
      // EN: Map over the cache array to replace only the updated record; re-sort to handle
      //     the case where the user changed the transaction date.
      // ES: Mapeamos sobre el array del cache para reemplazar solo el registro actualizado;
      //     re-ordenamos para manejar el caso donde el usuario cambió la fecha de transacción.
      queryClient.setQueryData<Transaction[]>(
        transactionKeys.all,
        (currentTransactions = []) =>
          sortTransactions(
            currentTransactions.map((transaction) =>
              transaction.id === updatedTransaction.id
                ? updatedTransaction
                : transaction,
            ),
          ),
      )
      startTransition(() => setActiveTransaction(updatedTransaction))
      showToast('Transaction updated')
    },
    onError: (mutationError) => {
      setServerError(extractApiError(mutationError))
    },
  })

  /**
   * EN: Delete mutation — calls DELETE /api/transactions/:id.
   *     The second argument to onSuccess is the original variable passed to mutateAsync,
   *     which here is the transactionId — used to filter it out of the cache.
   *
   * ES: Mutación de eliminación — llama a DELETE /api/transactions/:id.
   *     El segundo argumento de onSuccess es la variable original pasada a mutateAsync,
   *     que aquí es el transactionId — usado para filtrarlo del cache.
   */
  const deleteMutation = useMutation({
    mutationFn: deleteTransaction,
    onSuccess: (_, transactionId) => {
      setServerError(null)
      // EN: Remove the deleted transaction from the cache by filtering it out.
      // ES: Eliminamos la transacción borrada del cache filtrándola.
      queryClient.setQueryData<Transaction[]>(
        transactionKeys.all,
        (currentTransactions = []) =>
          currentTransactions.filter((transaction) => transaction.id !== transactionId),
      )
      // EN: Close the side panel if the deleted transaction was the one being edited.
      // ES: Cerramos el panel lateral si la transacción eliminada era la que se estaba editando.
      startTransition(() => setActiveTransaction(null))
      showToast('Transaction deleted')
    },
    onError: (mutationError) => {
      setServerError(extractApiError(mutationError))
    },
  })

  // EN: activeMutation is true while either the create or update mutation is in-flight.
  //     Used to disable the submit button and show the spinner in TransactionForm.
  //     deleteMutation is handled separately because it shows a confirmation dialog.
  // ES: activeMutation es true mientras la mutación de creación o actualización está en vuelo.
  //     Usado para deshabilitar el botón de envío y mostrar el spinner en TransactionForm.
  //     deleteMutation se maneja por separado porque muestra un diálogo de confirmación.
  const activeMutation = createMutation.isPending || updateMutation.isPending

  // EN: Derive the list-level error (shown inline in the panel, not in the form).
  // ES: Derivamos el error a nivel de lista (mostrado inline en el panel, no en el formulario).
  const listError = extractApiError(error)

  // ── Event Handlers ───────────────────────────────────────────────────────────────────────

  /**
   * EN: Open the side-panel editor for an existing transaction.
   *     Clears any server error from a previous mutation, closes the create modal if open,
   *     and sets activeTransaction (deferred to avoid blocking the click animation).
   *
   * ES: Abrimos el editor del panel lateral para una transacción existente.
   *     Limpia cualquier error del servidor de una mutación anterior, cierra el modal de creación
   *     si está abierto, y establece activeTransaction (diferido para evitar bloquear la animación del clic).
   */
  const handleEdit = (transaction: Transaction) => {
    setServerError(null)
    startTransition(() => {
      setIsCreateModalOpen(false)
      setActiveTransaction(transaction)
    })
  }

  /**
   * EN: Switch to create mode: clear the active transaction and open the create modal.
   *     Clearing activeTransaction prevents stale edit state from leaking into the modal form.
   *
   * ES: Cambiamos al modo de creación: limpiamos la transacción activa y abrimos el modal de creación.
   *     Limpiar activeTransaction previene que el estado de edición obsoleto se filtre al formulario del modal.
   */
  const handleCreateMode = () => {
    setServerError(null)
    startTransition(() => {
      setActiveTransaction(null)
      setIsCreateModalOpen(true)
    })
  }

  /**
   * EN: Close the create modal and clear any server error.
   *     Does not touch activeTransaction — the side panel state is independent.
   *
   * ES: Cerramos el modal de creación y limpiamos cualquier error del servidor.
   *     No toca activeTransaction — el estado del panel lateral es independiente.
   */
  const handleCloseCreateModal = () => {
    setServerError(null)
    setIsCreateModalOpen(false)
  }

  /**
   * EN: Close the side-panel editor by clearing activeTransaction.
   *
   * ES: Cerramos el editor del panel lateral limpiando activeTransaction.
   */
  const handleCloseEditor = () => {
    setServerError(null)
    setActiveTransaction(null)
  }

  /**
   * EN: Confirm deletion via window.confirm, then fire the delete mutation.
   *     Using async/await so any unhandled rejection from mutateAsync surfaces
   *     in the browser dev tools rather than being silently swallowed.
   *
   * ES: Confirmamos la eliminación via window.confirm, luego disparamos la mutación de eliminación.
   *     Usando async/await para que cualquier rechazo no manejado de mutateAsync aparezca
   *     en las herramientas dev del navegador en lugar de ser silenciosamente ignorado.
   */
  const handleDelete = async (transaction: Transaction) => {
    // EN: Native confirm dialog — simple and accessible; replaced by a custom dialog in production.
    // ES: Diálogo de confirmación nativo — simple y accesible; reemplazado por un diálogo personalizado en producción.
    if (
      !window.confirm(
        `Delete transaction #${transaction.id} for ${transaction.customerName}?`,
      )
    ) {
      return
    }

    await deleteMutation.mutateAsync(transaction.id)
  }

  /**
   * EN: Unified submit handler passed to both the create-modal form and the edit panel form.
   *     Branches on activeTransaction: if set → update; if null → create.
   *     TransactionForm calls this with the validated, shape-normalized payload.
   *
   * ES: Manejador de envío unificado pasado tanto al formulario del modal de creación como al formulario del panel de edición.
   *     Se bifurca en activeTransaction: si está establecido → actualizar; si es null → crear.
   *     TransactionForm llama a esto con el payload validado y normalizado en forma.
   */
  const handleSubmit = async (payload: TransactionPayload) => {
    if (activeTransaction) {
      await updateMutation.mutateAsync({
        transactionId: activeTransaction.id,
        payload,
      })
      return
    }

    await createMutation.mutateAsync(payload)
  }

  // ── Render ───────────────────────────────────────────────────────────────────────────────

  return (
    <div className="app-shell">
      {/* EN: Decorative background gradient orbs — purely visual, hidden from the accessibility tree. */}
      {/* ES: Orbs de gradiente de fondo decorativos — puramente visuales, ocultos del árbol de accesibilidad. */}
      <div className="background-orb background-orb--amber" aria-hidden="true" />
      <div className="background-orb background-orb--green" aria-hidden="true" />

      <main className="page">
        {/* ── Hero ─────────────────────────────────────────────────────────────────────── */}
        {/* EN: Hero section contains the app title, tagline, and the primary action buttons. */}
        {/* ES: La sección hero contiene el título de la app, tagline y los botones de acción primarios. */}
        <section className="hero" role="banner">
          <div className="hero__copy">
            <p className="eyebrow">Tenpo FullStack Challenge</p>
            <h1>Transaction desk built to stay calm under a hard rate limit.</h1>
            <p className="hero__summary">
              The UI fetches once, filters locally, and keeps the cache in sync after
              writes so the operator does not waste requests.
            </p>
          </div>

          <div className="hero__actions">
            {/* EN: Primary action: open the create modal. / ES: Acción primaria: abrir el modal de creación. */}
            <button
              className="primary-button"
              id="btn-new-transaction"
              onClick={handleCreateMode}
              type="button"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
              New transaction
            </button>
            {/* EN: Manual refresh — intentionally the only way to re-sync with the server after the
                    initial load, preserving rate-limit budget. Disabled while a refetch is in progress.
                ES: Actualización manual — intencionalmente la única forma de re-sincronizar con el servidor
                    después de la carga inicial, preservando el presupuesto del límite de tasa. Deshabilitado
                    mientras una actualización está en progreso. */}
            <button
              className="ghost-button"
              disabled={isFetching}
              id="btn-refresh"
              onClick={() => void refetch()}
              type="button"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="23 4 23 10 17 10" /><path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" /></svg>
              {isFetching ? 'Refreshing…' : 'Refresh'}
            </button>
          </div>
        </section>

        {/* ── Stats Grid ───────────────────────────────────────────────────────────────── */}
        {/* EN: Four summary cards computed from the currently visible (filtered) transaction set.
                All values update instantly when the filter changes — no server request needed.
            ES: Cuatro tarjetas de resumen calculadas del conjunto de transacciones visibles (filtrado) actual.
                Todos los valores se actualizan instantáneamente cuando el filtro cambia — no se necesita solicitud al servidor. */}
        <section className="stats-grid" aria-label="Transaction statistics">
          {/* EN: Count of visible transactions. / ES: Conteo de transacciones visibles. */}
          <article className="stat-card">
            <div className="stat-card__header">
              <div className="stat-card__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="2" y="3" width="20" height="14" rx="2" /><line x1="8" y1="21" x2="16" y2="21" /><line x1="12" y1="17" x2="12" y2="21" /></svg>
              </div>
              <span className="eyebrow">Visible movements</span>
            </div>
            <strong>{visibleTransactions.length}</strong>
            <p>Filtered locally to respect the API quota.</p>
          </article>

          {/* EN: Sum of visible amounts formatted as CLP currency.
                  formatCLP comes from lib/formatters.ts (DRY — single Intl.NumberFormat instance).
              ES: Suma de montos visibles formateados como moneda CLP.
                  formatCLP viene de lib/formatters.ts (DRY — instancia única de Intl.NumberFormat). */}
          <article className="stat-card">
            <div className="stat-card__header">
              <div className="stat-card__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="1" x2="12" y2="23" /><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" /></svg>
              </div>
              <span className="eyebrow">Visible amount</span>
            </div>
            <strong>{formatCLP(totalAmount)}</strong>
            <p>Immediate feedback for the current slice.</p>
          </article>

          {/* EN: Distinct customer count in the visible set (case-insensitive dedup).
              ES: Conteo de clientes distintos en el conjunto visible (deduplicación insensible a mayúsculas). */}
          <article className="stat-card">
            <div className="stat-card__header">
              <div className="stat-card__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" /><circle cx="9" cy="7" r="4" /><path d="M23 21v-2a4 4 0 0 0-3-3.87" /><path d="M16 3.13a4 4 0 0 1 0 7.75" /></svg>
              </div>
              <span className="eyebrow">Unique Tenpistas</span>
            </div>
            <strong>{uniqueCustomers}</strong>
            <p>Useful to spot concentration by customer.</p>
          </article>

          {/* EN: Date and customer of the most recent visible transaction.
                  Falls back to a placeholder when the list is empty.
              ES: Fecha y cliente de la transacción visible más reciente.
                  Retrocede a un marcador de posición cuando la lista está vacía. */}
          <article className="stat-card">
            <div className="stat-card__header">
              <div className="stat-card__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10" /><polyline points="12 6 12 12 16 14" /></svg>
              </div>
              <span className="eyebrow">Latest movement</span>
            </div>
            <strong>
              {latestTransaction
                ? dayjs(latestTransaction.transactionDate).format('DD MMM, HH:mm')
                : 'No data yet'}
            </strong>
            <p>
              {latestTransaction
                ? latestTransaction.customerName
                : 'Create the first record to populate the board.'}
            </p>
          </article>
        </section>

        {/* ── Workspace ────────────────────────────────────────────────────────────────── */}
        {/* EN: Two-column layout: main (list + filter) and aside (editor panel or empty state).
            ES: Layout de dos columnas: main (lista + filtro) y aside (panel editor o estado vacío). */}
        <section className="workspace">
          <div className="workspace__main">
            <div className="panel panel--list">
              <div className="panel__header">
                <div>
                  <p className="eyebrow">Customer lens</p>
                  <h2>Operations board</h2>
                </div>
                <p className="panel__hint">3 requests/min per client. Use refresh intentionally.</p>
              </div>

              {/* EN: Customer name filter — updates draftCustomerFilter on every keystroke.
                      The actual filtering uses deferredCustomerFilter for render performance.
                  ES: Filtro de nombre de cliente — actualiza draftCustomerFilter en cada pulsación de tecla.
                      El filtrado real usa deferredCustomerFilter para rendimiento de renderizado. */}
              <label className="search-field" htmlFor="customer-filter">
                <span>
                  <svg className="field-icon" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><circle cx="11" cy="11" r="8" /><line x1="21" y1="21" x2="16.65" y2="16.65" /></svg>
                  Filter by Tenpista name
                </span>
                <input
                  id="customer-filter"
                  onChange={(event) => setDraftCustomerFilter(event.target.value)}
                  placeholder="Try Camila Torres"
                  type="search"
                  value={draftCustomerFilter}
                />
              </label>

              {/* EN: Inline error shown when the initial fetch fails (e.g. 429 or 500 on load).
                  ES: Error inline mostrado cuando la obtención inicial falla (ej. 429 o 500 en la carga). */}
              {listError ? (
                <div className="server-error server-error--inline" role="alert">
                  <strong>{listError.error}</strong>
                  <p>{listError.message}</p>
                </div>
              ) : null}

              {/* EN: Show skeleton cards while the initial query is loading; render the real list once data arrives.
                  ES: Mostramos tarjetas skeleton mientras la consulta inicial está cargando; renderizamos la lista real cuando llegan los datos. */}
              {isLoading ? (
                <div className="skeleton-list" aria-label="Loading transactions">
                  <div className="skeleton-card" />
                  <div className="skeleton-card" />
                  <div className="skeleton-card" />
                </div>
              ) : (
                <TransactionList
                  activeTransactionId={activeTransaction?.id ?? null}
                  onDelete={(transaction) => void handleDelete(transaction)}
                  onEdit={handleEdit}
                  transactions={visibleTransactions}
                />
              )}
            </div>
          </div>

          {/* EN: Side panel — switches between the editor (when a transaction is selected) and
                  the empty/standby state (when nothing is selected).
              ES: Panel lateral — cambia entre el editor (cuando se selecciona una transacción) y
                  el estado vacío/standby (cuando no se selecciona nada). */}
          <aside className="workspace__side">
            {activeTransaction ? (
              <div className="panel panel--form">
                {/* EN: Edit form for the active transaction. serverError feeds field-level errors back into the form.
                    ES: Formulario de edición para la transacción activa. serverError alimenta los errores a nivel de campo de vuelta al formulario. */}
                <TransactionForm
                  activeTransaction={activeTransaction}
                  cancelLabel="Close editor"
                  isPending={activeMutation}
                  onCancelEdit={handleCloseEditor}
                  onSubmit={handleSubmit}
                  serverError={serverError}
                />

                {/* EN: Explanatory note card describing the cache-first strategy to evaluators.
                    ES: Tarjeta de nota explicativa que describe la estrategia cache-first a los evaluadores. */}
                <div className="note-card">
                  <p className="eyebrow">Challenge note</p>
                  <h3>Why this board avoids auto-refetches</h3>
                  <p>
                    A strict per-client rate limit makes aggressive background sync a bad
                    tradeoff. Cache updates keep the UX fast and predictable.
                  </p>
                  {/* EN: Live sync status — shows "updating" while a transition or delete is pending.
                      ES: Estado de sincronización en vivo — muestra "actualizando" mientras una transición o eliminación está pendiente. */}
                  <p className="note-card__status">
                    {isPanelPending || deleteMutation.isPending
                      ? '⟳ Updating local view…'
                      : '✓ Local cache is in sync with the latest mutation.'}
                  </p>
                </div>
              </div>
            ) : (
              /* EN: Empty state for the side panel when no transaction is selected.
                 ES: Estado vacío para el panel lateral cuando no hay transacción seleccionada. */
              <div className="panel panel--editor-empty">
                <div className="editor-empty__copy">
                  <p className="eyebrow">Editor standby</p>
                  <h2>Pick a record or start fresh</h2>
                  <p className="editor-empty__summary">
                    Existing movements open here for editing. New movements begin in a
                    focused modal so the board stays readable.
                  </p>
                </div>

                <div className="editor-empty__actions">
                  <button className="primary-button" onClick={handleCreateMode} type="button">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
                    New transaction
                  </button>
                </div>

                <div className="note-card">
                  <p className="eyebrow">Challenge note</p>
                  <h3>Why create lives in a modal</h3>
                  <p>
                    Editing is contextual, but creating is interruptive by nature. A modal
                    keeps the list state visible while giving the new record its own focus.
                  </p>
                  <p className="note-card__status">
                    {isPanelPending || createMutation.isPending || deleteMutation.isPending
                      ? '⟳ Updating local view…'
                      : '✓ Local cache is in sync with the latest mutation.'}
                  </p>
                </div>
              </div>
            )}
          </aside>
        </section>
      </main>

      {/* ── Create Modal ─────────────────────────────────────────────────────────────────── */}
      {/* EN: The create modal renders via React Portal to document.body (see Modal.tsx).
              TransactionForm inside uses variant="modal" for adjusted spacing.
          ES: El modal de creación se renderiza via React Portal a document.body (ver Modal.tsx).
              TransactionForm dentro usa variant="modal" para espaciado ajustado. */}
      <Modal
        description="Create a new transaction without replacing the current board context."
        isOpen={isCreateModalOpen}
        onClose={handleCloseCreateModal}
        title="Create transaction"
      >
        <div className="create-flow">
          {/* EN: Hero section inside the modal — explains the purpose and constraints to the operator.
              ES: Sección hero dentro del modal — explica el propósito y restricciones al operador. */}
          <section className="create-flow__hero">
            <div className="create-flow__hero-copy">
              <p className="eyebrow">New movement</p>
              <h3>Capture the transaction once and keep the board calm.</h3>
              <p>
                The modal validates the risky parts up front so operators can
                move quickly without sending noisy retries into a hard rate limit.
              </p>
            </div>

            {/* EN: Business rule pills — visual reminder of validation constraints.
                ES: Píldoras de reglas de negocio — recordatorio visual de las restricciones de validación. */}
            <div className="create-flow__rules" aria-label="Creation rules">
              <span className="create-flow__rule">No negative amount</span>
              <span className="create-flow__rule">No future date</span>
              <span className="create-flow__rule">100 tx per customer</span>
            </div>
          </section>

          {/* EN: Two-column layout inside the modal: explanatory aside + the actual form.
              ES: Layout de dos columnas dentro del modal: aside explicativo + el formulario real. */}
          <div className="create-flow__layout">
            <aside className="create-flow__aside">
              <p className="eyebrow">Before saving</p>
              <h4>What the operator should confirm</h4>
              <div className="create-flow__checklist">
                <p>
                  Amounts are stored as integers, so avoid decimal formatting or
                  values outside the supported range.
                </p>
                <p>
                  Customer names are normalized on save, which keeps search and
                  counting stable even if spacing varies.
                </p>
                <p>
                  The local cache updates immediately after success, so the list
                  stays current without an extra refresh.
                </p>
              </div>
            </aside>

            <div className="create-flow__form-shell">
              {/* EN: TransactionForm in modal variant — heading hidden because Modal provides its own.
                      activeTransaction=null signals create mode.
                  ES: TransactionForm en variante modal — encabezado oculto porque Modal provee el suyo.
                      activeTransaction=null señala modo de creación. */}
              <TransactionForm
                activeTransaction={null}
                isPending={createMutation.isPending}
                onCancelEdit={handleCloseCreateModal}
                onSubmit={handleSubmit}
                resetLabel="Clear draft"
                serverError={serverError}
                showHeading={false}
                variant="modal"
              />
            </div>
          </div>
        </div>
      </Modal>

      {/* ── Toast Notification ───────────────────────────────────────────────────────────── */}
      {/* EN: Accessible live region — screen readers announce the message when it appears.
              role="status" uses polite mode (does not interrupt ongoing announcements).
          ES: Región activa accesible — los lectores de pantalla anuncian el mensaje cuando aparece.
              role="status" usa modo polite (no interrumpe los anuncios en curso). */}
      {toast ? <div className="toast" role="status" aria-live="polite">{toast}</div> : null}
    </div>
  )
}

export default App
