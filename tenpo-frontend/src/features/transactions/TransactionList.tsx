import dayjs from 'dayjs'
import type { Transaction } from './types'

type TransactionListProps = {
  activeTransactionId: number | null
  onDelete: (transaction: Transaction) => void
  onEdit: (transaction: Transaction) => void
  transactions: Transaction[]
}

const currency = new Intl.NumberFormat('es-CL', {
  style: 'currency',
  currency: 'CLP',
  maximumFractionDigits: 0,
})

// EN: Generate a simple hash from any string to derive a hue value for card accents.
// ES: Generamos un hash simple de cualquier string para derivar un valor de matiz para acentos de tarjeta.
const stringToHue = (value: string): number => {
  let hash = 0
  for (let i = 0; i < value.length; i++) {
    hash = value.charCodeAt(i) + ((hash << 5) - hash)
  }
  return Math.abs(hash) % 360
}

// EN: Extract initials from a customer name to render an avatar badge.
// ES: Extraemos las iniciales del nombre del cliente para mostrar un badge de avatar.
const getInitials = (name: string): string => {
  const parts = name.trim().split(/\s+/)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  }
  return (parts[0]?.[0] ?? '?').toUpperCase()
}

export function TransactionList({
  activeTransactionId,
  onDelete,
  onEdit,
  transactions,
}: TransactionListProps) {
  if (!transactions.length) {
    return (
      <div className="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="9" y1="15" x2="15" y2="15" />
        </svg>
        <p className="eyebrow">No transactions in this view</p>
        <h3>Everything is clean.</h3>
        <p>
          Adjust the customer filter or create the first movement from the new
          transaction modal.
        </p>
      </div>
    )
  }

  return (
    <div className="transaction-list">
      {transactions.map((transaction, index) => {
        const hue = stringToHue(transaction.merchant)
        const cardAccent = `hsl(${hue}, 60%, 55%)`

        return (
          <article
            className={
              transaction.id === activeTransactionId
                ? 'transaction-card transaction-card--active'
                : 'transaction-card'
            }
            key={transaction.id}
            style={{
              '--stagger': index,
              '--card-accent': cardAccent,
            } as React.CSSProperties}
          >
            <div className="transaction-card__header">
              <div className="transaction-card__header-left">
                <span
                  className="customer-avatar"
                  aria-hidden="true"
                  style={{ background: `hsla(${hue}, 50%, 50%, 0.15)`, color: cardAccent }}
                >
                  {getInitials(transaction.customerName)}
                </span>
                <div>
                  <p className="transaction-card__merchant">{transaction.merchant}</p>
                  <p className="transaction-card__customer">
                    {transaction.customerName}
                  </p>
                </div>
              </div>
              <span className="pill">#{transaction.id}</span>
            </div>

            <div className="transaction-card__body">
              <strong>{currency.format(transaction.amountInPesos)}</strong>
              <span>{dayjs(transaction.transactionDate).format('DD MMM YYYY, HH:mm')}</span>
            </div>

            <div className="transaction-card__actions">
              <button
                className="ghost-button"
                onClick={() => onEdit(transaction)}
                type="button"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" /><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" /></svg>
                Edit
              </button>
              <button
                className="danger-button"
                onClick={() => onDelete(transaction)}
                type="button"
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" /></svg>
                Delete
              </button>
            </div>
          </article>
        )
      })}
    </div>
  )
}
