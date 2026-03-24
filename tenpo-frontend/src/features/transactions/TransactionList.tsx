import dayjs from 'dayjs'
import { formatCLP } from '../../lib/formatters'
import type { Transaction } from './types'

/**
 * EN: Props for the TransactionList component.
 *
 * ES: Props para el componente TransactionList.
 */
type TransactionListProps = {
  /** EN: Id of the currently selected transaction for visual highlight. / ES: Id de la transacción seleccionada actualmente para resalte visual. */
  activeTransactionId: number | null
  /** EN: Callback when the user clicks the Delete button on a card. / ES: Callback cuando el usuario hace clic en el botón Eliminar de una tarjeta. */
  onDelete: (transaction: Transaction) => void
  /** EN: Callback when the user clicks the Edit button on a card. / ES: Callback cuando el usuario hace clic en el botón Editar de una tarjeta. */
  onEdit: (transaction: Transaction) => void
  /** EN: The list of transactions to render. / ES: La lista de transacciones a renderizar. */
  transactions: Transaction[]
}

// ── Utility Helpers ───────────────────────────────────────────────────────────────────────

/**
 * EN: Generate a simple hash from any string to derive a hue value for card accent colors.
 *     The hash is computed by iterating over char codes and bit-shifting — a common,
 *     fast non-cryptographic hash. The result is clamped to [0, 360) with Math.abs and modulo.
 *     This produces a consistent color for the same merchant string across re-renders.
 *
 * ES: Generamos un hash simple de cualquier string para derivar un valor de matiz para los colores
 *     de acento de las tarjetas. El hash se calcula iterando sobre los códigos de caracteres y
 *     desplazamiento de bits — un hash no criptográfico común y rápido. El resultado se limita a
 *     [0, 360) con Math.abs y módulo. Esto produce un color consistente para el mismo string de
 *     comercio entre re-renderizaciones.
 *
 * @param value - the string to hash / la cadena a hashear
 * @returns a hue value in [0, 360) / un valor de matiz en [0, 360)
 */
const stringToHue = (value: string): number => {
  let hash = 0
  for (let i = 0; i < value.length; i++) {
    // EN: Classic djb2-inspired hash: shift and XOR for good distribution.
    // ES: Hash clásico inspirado en djb2: desplazamiento y XOR para buena distribución.
    hash = value.charCodeAt(i) + ((hash << 5) - hash)
  }
  return Math.abs(hash) % 360
}

/**
 * EN: Extract initials from a customer name to render an avatar badge.
 *     If the name has two or more words, use the first letter of the first and last word.
 *     If only one word, use its first letter.
 *     Falls back to '?' if the name is empty.
 *
 * ES: Extraemos las iniciales del nombre del cliente para mostrar un badge de avatar.
 *     Si el nombre tiene dos o más palabras, usamos la primera letra de la primera y última palabra.
 *     Si solo hay una palabra, usamos su primera letra.
 *     Cae en '?' si el nombre está vacío.
 *
 * @param name - the customer name / el nombre del cliente
 * @returns 1-2 upper-case initials / 1-2 iniciales en mayúsculas
 */
const getInitials = (name: string): string => {
  const parts = name.trim().split(/\s+/)
  if (parts.length >= 2) {
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
  }
  return (parts[0]?.[0] ?? '?').toUpperCase()
}

// ── Component ─────────────────────────────────────────────────────────────────────────────

/**
 * EN: Renders either an empty state or a list of transaction cards.
 *     Each card shows the merchant, customer name, avatar (initials), amount, and date.
 *     The active card (being edited) receives the --active modifier for visual feedback.
 *     Card accent colors are derived from the merchant name hash so each merchant
 *     has a consistent identifying hue without requiring backend support.
 *
 * ES: Renderiza ya sea un estado vacío o una lista de tarjetas de transacción.
 *     Cada tarjeta muestra el comercio, nombre del cliente, avatar (iniciales), monto y fecha.
 *     La tarjeta activa (en edición) recibe el modificador --active para retroalimentación visual.
 *     Los colores de acento de tarjeta se derivan del hash del nombre del comercio para que cada
 *     comercio tenga un matiz identificador consistente sin requerir soporte del backend.
 *
 * Design — SOLID:
 *   SRP : Responsible only for rendering the list and individual cards; no data fetching or mutation.
 */
export function TransactionList({
  activeTransactionId,
  onDelete,
  onEdit,
  transactions,
}: TransactionListProps) {
  // EN: Show the empty state when no transactions match the current filter or when the list is empty.
  //     This is an explicit design choice (vs. rendering nothing) to communicate state clearly.
  // ES: Mostramos el estado vacío cuando no hay transacciones que coincidan con el filtro actual
  //     o cuando la lista está vacía. Esta es una elección de diseño explícita (vs. no renderizar nada)
  //     para comunicar el estado claramente.
  if (!transactions.length) {
    return (
      <div className="empty-state">
        {/* EN: Decorative icon, hidden from AT with aria-hidden. / ES: Icono decorativo, oculto de los AT con aria-hidden. */}
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
        // EN: Compute a unique hue from the merchant name for the card's left accent bar.
        //     The same merchant always gets the same hue, making the UI feel consistent.
        // ES: Calculamos un matiz único del nombre del comercio para la barra de acento izquierda de la tarjeta.
        //     El mismo comercio siempre obtiene el mismo matiz, haciendo que la UI se sienta consistente.
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
              // EN: CSS custom properties set per-card so the animation stagger and accent color
              //     can be applied from CSS without JS-side inline style manipulation.
              // ES: Propiedades CSS personalizadas establecidas por tarjeta para que el escalonamiento
              //     de animación y el color de acento puedan aplicarse desde CSS sin manipulación de
              //     estilo inline del lado de JS.
              '--stagger': index,
              '--card-accent': cardAccent,
            } as React.CSSProperties}
          >
            {/* ── Card Header: avatar + merchant + customer + id pill ─────────────────── */}
            <div className="transaction-card__header">
              <div className="transaction-card__header-left">
                {/* EN: Avatar badge shows initials with a tinted background derived from the merchant hue. / ES: Badge de avatar muestra iniciales con fondo teñido derivado del matiz del comercio. */}
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
              {/* EN: ID pill for quick identification when looking up a transaction. / ES: Píldora de ID para identificación rápida al buscar una transacción. */}
              <span className="pill">#{transaction.id}</span>
            </div>

            {/* ── Card Body: amount + formatted date ──────────────────────────────────── */}
            <div className="transaction-card__body">
              {/* EN: Format amount using the shared CLP formatter (DRY fix: no inline Intl.NumberFormat). / ES: Formateamos el monto usando el formateador CLP compartido (corrección DRY: sin Intl.NumberFormat inline). */}
              <strong>{formatCLP(transaction.amountInPesos)}</strong>
              {/* EN: dayjs formats the ISO-8601 string into a human-readable locale date. / ES: dayjs formatea la cadena ISO-8601 en una fecha legible por humanos. */}
              <span>{dayjs(transaction.transactionDate).format('DD MMM YYYY, HH:mm')}</span>
            </div>

            {/* ── Card Actions: Edit + Delete ──────────────────────────────────────────── */}
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
