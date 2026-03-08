import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import {
  getInitialFormValues,
  MAX_AMOUNT_IN_PESOS,
  toPayload,
  transactionFormSchema,
  type TransactionFormValues,
} from './schema'
import type { ApiError, Transaction, TransactionPayload } from './types'

type TransactionFormProps = {
  activeTransaction: Transaction | null
  cancelLabel?: string
  isPending: boolean
  resetLabel?: string
  serverError: ApiError | null
  onCancelEdit: () => void
  onSubmit: (payload: TransactionPayload) => Promise<void> | void
  showHeading?: boolean
  variant?: 'modal' | 'panel'
}

export function TransactionForm({
  activeTransaction,
  cancelLabel,
  isPending,
  resetLabel,
  serverError,
  onCancelEdit,
  onSubmit,
  showHeading = true,
  variant = 'panel',
}: TransactionFormProps) {
  const form = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionFormSchema),
    defaultValues: getInitialFormValues(activeTransaction),
  })

  // EN: Reset form values whenever the active transaction changes, keeping the editor in sync.
  // ES: Resetear valores del formulario cuando cambien las transacciones activas, sincronizando el editor.
  useEffect(() => {
    form.reset(getInitialFormValues(activeTransaction))
  }, [activeTransaction, form])

  // EN: Push server-side field errors back into the form so they appear inline.
  // ES: Enviamos los errores de campo del servidor de vuelta al formulario para que aparezcan inline.
  useEffect(() => {
    const fieldErrors = serverError?.fieldErrors ?? []

    if (!fieldErrors.length) {
      return
    }

    fieldErrors.forEach((fieldError) => {
      const fieldName = fieldError.field as keyof TransactionFormValues
      form.setError(fieldName, { message: fieldError.message })
    })
  }, [form, serverError])

  const submitLabel = activeTransaction ? 'Save changes' : 'Create transaction'
  const resolvedResetLabel = resetLabel ?? 'Reset form'

  return (
    <form
      className={`transaction-form transaction-form--${variant}`}
      onSubmit={form.handleSubmit(
        (values) => onSubmit(toPayload(values)),
        () => {
          const el = document.querySelector('.transaction-form')
          if (el) {
            el.classList.remove('form-shake')
            void (el as HTMLElement).offsetWidth
            el.classList.add('form-shake')
          }
        },
      )}
    >
      {showHeading ? (
        <div className="form-heading">
          <div>
            <p className="eyebrow">
              {activeTransaction ? 'Editing selected record' : 'Create a new record'}
            </p>
            <h2>{activeTransaction ? 'Transaction editor' : 'New transaction'}</h2>
          </div>
          {cancelLabel ? (
            <button
              className="ghost-button"
              onClick={onCancelEdit}
              type="button"
            >
              {cancelLabel}
            </button>
          ) : null}
        </div>
      ) : null}

      <label className="field">
        <span>
          <span className="field-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="1" x2="12" y2="23" /><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" /></svg>
          </span>
          Amount in pesos
        </span>
        <input
          {...form.register('amountInPesos', { valueAsNumber: true })}
          autoComplete="off"
          inputMode="numeric"
          max={MAX_AMOUNT_IN_PESOS}
          min={0}
          type="number"
        />
        <small>{form.formState.errors.amountInPesos?.message}</small>
      </label>

      <label className="field">
        <span>
          <span className="field-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" /><polyline points="9 22 9 12 15 12 15 22" /></svg>
          </span>
          Merchant or category
        </span>
        <input
          {...form.register('merchant')}
          autoComplete="organization"
          maxLength={160}
          placeholder="Example: Supermercado Lider"
          type="text"
        />
        <small>{form.formState.errors.merchant?.message}</small>
      </label>

      <label className="field">
        <span>
          <span className="field-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></svg>
          </span>
          Tenpista name
        </span>
        <input
          {...form.register('customerName')}
          autoComplete="name"
          maxLength={120}
          placeholder="Example: Camila Torres"
          type="text"
        />
        <small>{form.formState.errors.customerName?.message}</small>
      </label>

      <label className="field">
        <span>
          <span className="field-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" /></svg>
          </span>
          Transaction date and time
        </span>
        <input
          {...form.register('transactionDate')}
          max={new Date().toISOString().slice(0, 16)}
          type="datetime-local"
        />
        <small>{form.formState.errors.transactionDate?.message}</small>
      </label>

      {serverError && !(serverError.fieldErrors ?? []).length ? (
        <div className="server-error" role="alert">
          <strong>{serverError.error}</strong>
          <p>{serverError.message}</p>
        </div>
      ) : null}

      <div className={`form-actions form-actions--${variant}`}>
        <button className="primary-button" disabled={isPending} type="submit">
          {isPending ? (
            <>
              <span className="btn-spinner" aria-hidden="true" />
              Saving…
            </>
          ) : submitLabel}
        </button>
        <button
          className="ghost-button"
          disabled={isPending}
          onClick={() => form.reset(getInitialFormValues(activeTransaction))}
          type="button"
        >
          {resolvedResetLabel}
        </button>
      </div>
    </form>
  )
}
