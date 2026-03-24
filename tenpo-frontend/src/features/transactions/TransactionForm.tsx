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

/**
 * EN: Props for the TransactionForm component.
 *     The form can be used in two contexts:
 *       - 'panel' variant: the edit form in the side panel (activeTransaction is set)
 *       - 'modal' variant: the create form inside the modal (activeTransaction is null)
 *     Behavior adapts based on whether activeTransaction is provided.
 *
 * ES: Props para el componente TransactionForm.
 *     El formulario puede usarse en dos contextos:
 *       - Variante 'panel': el formulario de edición en el panel lateral (activeTransaction está establecido)
 *       - Variante 'modal': el formulario de creación dentro del modal (activeTransaction es null)
 *     El comportamiento se adapta basado en si se proporciona activeTransaction.
 */
type TransactionFormProps = {
  /** EN: The transaction being edited, or null when creating. / ES: La transacción siendo editada, o null al crear. */
  activeTransaction: Transaction | null
  /** EN: Optional label for the cancel/close button in the heading. / ES: Etiqueta opcional para el botón de cancelar/cerrar en el encabezado. */
  cancelLabel?: string
  /** EN: True while the create/update mutation is in-flight — disables the submit button and shows spinner. / ES: True mientras la mutación de creación/actualización está en vuelo — deshabilita el botón de envío y muestra spinner. */
  isPending: boolean
  /** EN: Optional label override for the reset button. Defaults to "Reset form". / ES: Etiqueta opcional de sobreescritura para el botón de reinicio. Predetermina a "Reset form". */
  resetLabel?: string
  /** EN: API error from the last failed mutation — field errors are mapped back into the form. / ES: Error de API del último mutation fallido — los errores de campo se mapean de vuelta al formulario. */
  serverError: ApiError | null
  /** EN: Called when the user clicks the cancel/close button. / ES: Llamado cuando el usuario hace clic en el botón de cancelar/cerrar. */
  onCancelEdit: () => void
  /** EN: Called when the form passes all validation. Receives the shaped API payload. / ES: Llamado cuando el formulario pasa toda la validación. Recibe el payload de API formado. */
  onSubmit: (payload: TransactionPayload) => Promise<void> | void
  /** EN: Whether to render the heading section (can be hidden in modal where the modal header handles it). / ES: Si se renderiza la sección de encabezado (puede ocultarse en el modal donde el encabezado del modal lo maneja). */
  showHeading?: boolean
  /** EN: Layout variant: 'panel' (default) or 'modal'. Controls spacing and action layout via CSS modifier. / ES: Variante de layout: 'panel' (predeterminado) o 'modal'. Controla el espaciado y layout de acciones via modificador CSS. */
  variant?: 'modal' | 'panel'
}

/**
 * EN: Controlled transaction form used for both creating and editing transactions.
 *     Integrates react-hook-form with a Zod schema for real-time validation.
 *     Server-side field errors are fed back into the form state so they appear
 *     inline next to the failing field without a page reload.
 *
 * ES: Formulario de transacción controlado usado tanto para crear como editar transacciones.
 *     Integra react-hook-form con un esquema Zod para validación en tiempo real.
 *     Los errores de campo del lado del servidor se alimentan de vuelta al estado del formulario
 *     para que aparezcan inline junto al campo fallido sin una recarga de página.
 *
 * Design — SOLID:
 *   SRP : Owns only form state and rendering; no API calls or cache management.
 *   OCP : New fields can be added by extending the schema and adding JSX — no structural change needed.
 */
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
  // EN: Initialize react-hook-form with the Zod resolver so validation runs against
  //     the transactionFormSchema on every field change and on submit.
  // ES: Inicializamos react-hook-form con el resolver Zod para que la validación se ejecute
  //     contra transactionFormSchema en cada cambio de campo y en el envío.
  const form = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionFormSchema),
    defaultValues: getInitialFormValues(activeTransaction),
  })

  // EN: Reset form values whenever the active transaction changes, keeping the editor in sync.
  //     This handles the scenario where the user switches from one transaction to another
  //     without unmounting the form component.
  // ES: Resetear valores del formulario cuando cambien las transacciones activas, sincronizando el editor.
  //     Esto maneja el escenario donde el usuario cambia de una transacción a otra sin desmontar el componente.
  useEffect(() => {
    form.reset(getInitialFormValues(activeTransaction))
  }, [activeTransaction, form])

  // EN: Push server-side field errors back into the form so they appear inline.
  //     When the server returns field-level validation errors (e.g. future date slipping
  //     through frontend validation due to clock skew), they are mapped to the correct field.
  // ES: Enviamos los errores de campo del servidor de vuelta al formulario para que aparezcan inline.
  //     Cuando el servidor devuelve errores de validación a nivel de campo (ej. fecha futura que
  //     pasa la validación del frontend por desfase de reloj), se mapean al campo correcto.
  useEffect(() => {
    const fieldErrors = serverError?.fieldErrors ?? []

    // EN: Skip if there are no field-level errors (generic message shown separately).
    // ES: Omitimos si no hay errores a nivel de campo (el mensaje genérico se muestra por separado).
    if (!fieldErrors.length) {
      return
    }

    fieldErrors.forEach((fieldError) => {
      const fieldName = fieldError.field as keyof TransactionFormValues
      form.setError(fieldName, { message: fieldError.message })
    })
  }, [form, serverError])

  // EN: Derive button labels based on whether we're in create or edit mode.
  // ES: Derivamos las etiquetas de botón basado en si estamos en modo de creación o edición.
  const submitLabel = activeTransaction ? 'Save changes' : 'Create transaction'
  const resolvedResetLabel = resetLabel ?? 'Reset form'

  return (
    <form
      className={`transaction-form transaction-form--${variant}`}
      onSubmit={form.handleSubmit(
        // EN: Success handler: called when all Zod validations pass.
        //     Converts form values to the API payload shape before calling onSubmit.
        // ES: Manejador de éxito: llamado cuando todas las validaciones Zod pasan.
        //     Convierte los valores del formulario a la forma del payload de API antes de llamar a onSubmit.
        (values) => onSubmit(toPayload(values)),
        // EN: Error handler: called when the form is submitted with validation failures.
        //     Triggers the CSS shake animation to give tactile feedback that submission failed.
        // ES: Manejador de error: llamado cuando el formulario se envía con fallos de validación.
        //     Activa la animación CSS de sacudida para dar retroalimentación táctil de que el envío falló.
        () => {
          const el = document.querySelector('.transaction-form')
          if (el) {
            // EN: Remove the class first, force a reflow, then re-add to restart the animation.
            //     Without the reflow, adding the same class twice does not restart the animation.
            // ES: Eliminamos la clase primero, forzamos un reflow, luego la volvemos a agregar para reiniciar la animación.
            //     Sin el reflow, agregar la misma clase dos veces no reinicia la animación.
            el.classList.remove('form-shake')
            void (el as HTMLElement).offsetWidth
            el.classList.add('form-shake')
          }
        },
      )}
    >
      {/* ── Heading (edit mode indicator + cancel button) ──────────────────────── */}
      {showHeading ? (
        <div className="form-heading">
          <div>
            {/* EN: Eyebrow context label switches between "Editing" and "Create" modes. / ES: La etiqueta eyebrow cambia entre modos "Editando" y "Crear". */}
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

      {/* ── Amount Field ─────────────────────────────────────────────────────────── */}
      <label className="field">
        <span>
          {/* EN: Decorative icon hidden from AT. / ES: Icono decorativo oculto de los AT. */}
          <span className="field-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="1" x2="12" y2="23" /><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" /></svg>
          </span>
          Amount in pesos
        </span>
        {/* EN: valueAsNumber tells react-hook-form to coerce the string value to a number
                before running Zod validation (the schema expects z.number()).
            ES: valueAsNumber le dice a react-hook-form que convierta el valor string a número
                antes de ejecutar la validación Zod (el esquema espera z.number()). */}
        <input
          {...form.register('amountInPesos', { valueAsNumber: true })}
          autoComplete="off"
          inputMode="numeric"
          max={MAX_AMOUNT_IN_PESOS}
          min={0}
          type="number"
        />
        {/* EN: Inline validation error message (empty when the field is valid). / ES: Mensaje de error de validación inline (vacío cuando el campo es válido). */}
        <small>{form.formState.errors.amountInPesos?.message}</small>
      </label>

      {/* ── Merchant Field ───────────────────────────────────────────────────────── */}
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

      {/* ── Customer Name Field ──────────────────────────────────────────────────── */}
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

      {/* ── Transaction Date Field ───────────────────────────────────────────────── */}
      <label className="field">
        <span>
          <span className="field-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2" /><line x1="16" y1="2" x2="16" y2="6" /><line x1="8" y1="2" x2="8" y2="6" /><line x1="3" y1="10" x2="21" y2="10" /></svg>
          </span>
          Transaction date and time
        </span>
        {/* EN: max attribute set to the current minute so the browser's native date picker
                also enforces the "no future date" rule (accessible, not just JS-side).
            ES: El atributo max se establece al minuto actual para que el selector de fecha
                nativo del navegador también haga cumplir la regla de "sin fecha futura"
                (accesible, no solo del lado de JS). */}
        <input
          {...form.register('transactionDate')}
          max={new Date().toISOString().slice(0, 16)}
          type="datetime-local"
        />
        <small>{form.formState.errors.transactionDate?.message}</small>
      </label>

      {/* ── Server Error Banner (non-field errors only) ──────────────────────────── */}
      {/* EN: Only shown when the server error has no field-level errors (e.g. 429 or 500).
              Field-level errors are already injected into the form above.
          ES: Solo se muestra cuando el error del servidor no tiene errores a nivel de campo (ej. 429 o 500).
              Los errores a nivel de campo ya están inyectados en el formulario arriba. */}
      {serverError && !(serverError.fieldErrors ?? []).length ? (
        <div className="server-error" role="alert">
          <strong>{serverError.error}</strong>
          <p>{serverError.message}</p>
        </div>
      ) : null}

      {/* ── Form Actions: Submit + Reset ─────────────────────────────────────────── */}
      <div className={`form-actions form-actions--${variant}`}>
        {/* EN: Disabled while pending to prevent double-submission. Shows a spinner label. / ES: Deshabilitado mientras está pendiente para prevenir doble envío. Muestra una etiqueta con spinner. */}
        <button className="primary-button" disabled={isPending} type="submit">
          {isPending ? (
            <>
              <span className="btn-spinner" aria-hidden="true" />
              Saving…
            </>
          ) : submitLabel}
        </button>
        {/* EN: Reset button restores the form to its initial state (either blank or the original transaction values). / ES: El botón de reinicio restaura el formulario a su estado inicial (ya sea en blanco o los valores originales de la transacción). */}
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
