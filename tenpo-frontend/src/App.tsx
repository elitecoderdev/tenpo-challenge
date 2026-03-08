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

const currency = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
})

// EN: Normalize backend error payloads into a consistent ApiError shape usable by the UI.
// ES: Normalizamos las respuestas de error del backend a una forma consistente de ApiError utilizable por la UI.
const normalizeApiError = (
  status: number | undefined,
  statusText: string | undefined,
  data: unknown,
): ApiError => {
  const fallbackMessage =
    typeof data === 'string' && data.trim()
      ? data.trim()
      : 'The server returned an unexpected response.'

  if (data && typeof data === 'object' && !Array.isArray(data)) {
    const candidate = data as Partial<ApiError>

    return {
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
      fieldErrors: Array.isArray(candidate.fieldErrors) ? candidate.fieldErrors : [],
    }
  }

  return {
    timestamp: new Date().toISOString(),
    status: status ?? 500,
    error: statusText?.trim() || 'Request failed',
    message: fallbackMessage,
    path: '',
    fieldErrors: [],
  }
}

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

const sortTransactions = (transactions: Transaction[]) =>
  [...transactions].sort((left, right) => {
    const byDate =
      dayjs(right.transactionDate).valueOf() - dayjs(left.transactionDate).valueOf()

    return byDate !== 0 ? byDate : right.id - left.id
  })

function App() {
  const queryClient = useQueryClient()
  const [draftCustomerFilter, setDraftCustomerFilter] = useState('')
  const [activeTransaction, setActiveTransaction] = useState<Transaction | null>(null)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [serverError, setServerError] = useState<ApiError | null>(null)
  const [isPanelPending, startTransition] = useTransition()
  const [toast, setToast] = useState<string | null>(null)
  const toastTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const deferredCustomerFilter = useDeferredValue(draftCustomerFilter)

  const { data: transactions = [], error, isFetching, isLoading, refetch } = useQuery(
    transactionsQueryOptions,
  )

  const visibleTransactions = transactions.filter((transaction) =>
    transaction.customerName
      .toLowerCase()
      .includes(deferredCustomerFilter.trim().toLowerCase()),
  )

  const totalAmount = visibleTransactions.reduce(
    (sum, transaction) => sum + transaction.amountInPesos,
    0,
  )
  const uniqueCustomers = new Set(
    visibleTransactions.map((transaction) => transaction.customerName.toLowerCase()),
  ).size
  const latestTransaction = visibleTransactions[0]

  // EN: Show a toast notification that auto-dismisses after 3 seconds.
  // ES: Mostramos una notificación toast que se oculta automáticamente después de 3 segundos.
  const showToast = (message: string) => {
    if (toastTimer.current) clearTimeout(toastTimer.current)
    setToast(message)
    toastTimer.current = setTimeout(() => setToast(null), 3000)
  }

  useEffect(() => {
    return () => {
      if (toastTimer.current) clearTimeout(toastTimer.current)
    }
  }, [])

  const createMutation = useMutation({
    mutationFn: createTransaction,
    onSuccess: (createdTransaction) => {
      setServerError(null)
      // EN: Update the cache eagerly so we do not burn the 3 req/min quota on an extra refetch.
      // ES: Actualizamos el cache de inmediato para no gastar el limite de 3 req/min en un refetch adicional.
      queryClient.setQueryData<Transaction[]>(
        transactionKeys.all,
        (currentTransactions = []) =>
          sortTransactions([createdTransaction, ...currentTransactions]),
      )
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

  const deleteMutation = useMutation({
    mutationFn: deleteTransaction,
    onSuccess: (_, transactionId) => {
      setServerError(null)
      queryClient.setQueryData<Transaction[]>(
        transactionKeys.all,
        (currentTransactions = []) =>
          currentTransactions.filter((transaction) => transaction.id !== transactionId),
      )
      startTransition(() => setActiveTransaction(null))
      showToast('Transaction deleted')
    },
    onError: (mutationError) => {
      setServerError(extractApiError(mutationError))
    },
  })

  const activeMutation = createMutation.isPending || updateMutation.isPending
  const listError = extractApiError(error)

  const handleEdit = (transaction: Transaction) => {
    setServerError(null)
    startTransition(() => {
      setIsCreateModalOpen(false)
      setActiveTransaction(transaction)
    })
  }

  const handleCreateMode = () => {
    setServerError(null)
    startTransition(() => {
      setActiveTransaction(null)
      setIsCreateModalOpen(true)
    })
  }

  const handleCloseCreateModal = () => {
    setServerError(null)
    setIsCreateModalOpen(false)
  }

  const handleCloseEditor = () => {
    setServerError(null)
    setActiveTransaction(null)
  }

  const handleDelete = async (transaction: Transaction) => {
    if (
      !window.confirm(
        `Delete transaction #${transaction.id} for ${transaction.customerName}?`,
      )
    ) {
      return
    }

    await deleteMutation.mutateAsync(transaction.id)
  }

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

  return (
    <div className="app-shell">
      <div className="background-orb background-orb--amber" aria-hidden="true" />
      <div className="background-orb background-orb--green" aria-hidden="true" />

      <main className="page">
        {/* ── Hero ── */}
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
            <button
              className="primary-button"
              id="btn-new-transaction"
              onClick={handleCreateMode}
              type="button"
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round"><line x1="12" y1="5" x2="12" y2="19" /><line x1="5" y1="12" x2="19" y2="12" /></svg>
              New transaction
            </button>
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

        {/* ── Stats ── */}
        <section className="stats-grid" aria-label="Transaction statistics">
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

          <article className="stat-card">
            <div className="stat-card__header">
              <div className="stat-card__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="1" x2="12" y2="23" /><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" /></svg>
              </div>
              <span className="eyebrow">Visible amount</span>
            </div>
            <strong>{currency.format(totalAmount)}</strong>
            <p>Immediate feedback for the current slice.</p>
          </article>

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

        {/* ── Workspace ── */}
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

              {listError ? (
                <div className="server-error server-error--inline" role="alert">
                  <strong>{listError.error}</strong>
                  <p>{listError.message}</p>
                </div>
              ) : null}

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

          <aside className="workspace__side">
            {activeTransaction ? (
              <div className="panel panel--form">
                <TransactionForm
                  activeTransaction={activeTransaction}
                  cancelLabel="Close editor"
                  isPending={activeMutation}
                  onCancelEdit={handleCloseEditor}
                  onSubmit={handleSubmit}
                  serverError={serverError}
                />

                <div className="note-card">
                  <p className="eyebrow">Challenge note</p>
                  <h3>Why this board avoids auto-refetches</h3>
                  <p>
                    A strict per-client rate limit makes aggressive background sync a bad
                    tradeoff. Cache updates keep the UX fast and predictable.
                  </p>
                  <p className="note-card__status">
                    {isPanelPending || deleteMutation.isPending
                      ? '⟳ Updating local view…'
                      : '✓ Local cache is in sync with the latest mutation.'}
                  </p>
                </div>
              </div>
            ) : (
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

      <Modal
        description="Create a new transaction without replacing the current board context."
        isOpen={isCreateModalOpen}
        onClose={handleCloseCreateModal}
        title="Create transaction"
      >
        <div className="create-flow">
          <section className="create-flow__hero">
            <div className="create-flow__hero-copy">
              <p className="eyebrow">New movement</p>
              <h3>Capture the transaction once and keep the board calm.</h3>
              <p>
                The modal validates the risky parts up front so operators can
                move quickly without sending noisy retries into a hard rate limit.
              </p>
            </div>

            <div className="create-flow__rules" aria-label="Creation rules">
              <span className="create-flow__rule">No negative amount</span>
              <span className="create-flow__rule">No future date</span>
              <span className="create-flow__rule">100 tx per customer</span>
            </div>
          </section>

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

      {/* ── Toast ── */}
      {toast ? <div className="toast" role="status" aria-live="polite">{toast}</div> : null}
    </div>
  )
}

export default App
