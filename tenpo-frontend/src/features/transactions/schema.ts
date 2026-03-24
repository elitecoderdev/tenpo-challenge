import dayjs from 'dayjs'
import { z } from 'zod'
import type { Transaction, TransactionPayload } from './types'

/**
 * EN: Zod validation schema and form utilities for the transaction create/edit form.
 *     This module is the single source of truth for frontend validation rules:
 *       - amountInPesos: integer, >= 0, <= Integer.MAX_VALUE
 *       - merchant: required, max 160 chars
 *       - customerName: required, max 120 chars
 *       - transactionDate: valid date-time, not in the future
 *
 *     The rules mirror the backend Bean Validation constraints so that both sides
 *     enforce the same contract. Frontend validation provides immediate user feedback;
 *     backend validation protects the API even without the UI.
 *
 *     Performance note: the schema is defined at module level (not inside a component)
 *     so it is only created once per module load, not on every render.
 *
 * ES: Esquema de validación Zod y utilidades de formulario para el formulario de creación/edición de transacciones.
 *     Este módulo es la única fuente de verdad para las reglas de validación del frontend:
 *       - amountInPesos: entero, >= 0, <= Integer.MAX_VALUE
 *       - merchant: requerido, máx 160 caracteres
 *       - customerName: requerido, máx 120 caracteres
 *       - transactionDate: fecha-hora válida, no en el futuro
 *
 *     Las reglas reflejan las restricciones de Bean Validation del backend para que ambos lados
 *     hagan cumplir el mismo contrato. La validación del frontend proporciona retroalimentación
 *     inmediata al usuario; la validación del backend protege la API incluso sin la UI.
 */

// ── Constants ─────────────────────────────────────────────────────────────────────────────

/**
 * EN: Maximum allowed amount value, equal to Integer.MAX_VALUE (2,147,483,647).
 *     Must match the @Max constraint in TransactionRequest.java.
 *     Exported so the form input can set the HTML max attribute for accessibility.
 *
 * ES: Valor máximo permitido de monto, igual a Integer.MAX_VALUE (2.147.483.647).
 *     Debe coincidir con la restricción @Max en TransactionRequest.java.
 *     Exportado para que el input del formulario pueda establecer el atributo max HTML para accesibilidad.
 */
export const MAX_AMOUNT_IN_PESOS = 2_147_483_647

// ── Validation Schema ─────────────────────────────────────────────────────────────────────

/**
 * EN: Zod schema for the transaction form. Each field is validated independently
 *     with a descriptive error message shown inline below the failing field.
 *
 * ES: Esquema Zod para el formulario de transacción. Cada campo se valida independientemente
 *     con un mensaje de error descriptivo mostrado inline debajo del campo fallido.
 */
export const transactionFormSchema = z.object({
  // EN: Amount must be an integer (no decimals) within [0, Integer.MAX_VALUE].
  //     .int() rejects fractional numbers before the range check.
  // ES: El monto debe ser un entero (sin decimales) dentro de [0, Integer.MAX_VALUE].
  //     .int() rechaza números fraccionarios antes de la verificación de rango.
  amountInPesos: z
    .number()
    .int('Amount must be an integer.')
    .min(0, 'Amount cannot be negative.')
    .max(
      MAX_AMOUNT_IN_PESOS,
      `Amount must stay below ${MAX_AMOUNT_IN_PESOS.toLocaleString('en-US')}.`,
    ),

  // EN: Merchant is required (non-empty after trim) and limited to 160 characters.
  //     .trim() is applied by Zod before length checks so leading/trailing spaces
  //     don't inflate the character count or produce an empty submission.
  // ES: El comercio es requerido (no vacío después de trim) y limitado a 160 caracteres.
  //     .trim() es aplicado por Zod antes de las verificaciones de longitud para que
  //     los espacios al inicio/final no inflen el conteo de caracteres o produzcan un envío vacío.
  merchant: z
    .string()
    .trim()
    .min(1, 'Merchant is required.')
    .max(160, 'Merchant must stay under 160 characters.'),

  // EN: Customer name is required and capped at 120 characters (matches backend constraint).
  // ES: El nombre del cliente es requerido y limitado a 120 caracteres (coincide con restricción del backend).
  customerName: z
    .string()
    .trim()
    .min(1, 'Tenpista name is required.')
    .max(120, 'Tenpista name must stay under 120 characters.'),

  // EN: Date-time string from the datetime-local input.
  //     First refine checks that dayjs can parse it (guards against browser inconsistencies).
  //     Second refine enforces the "no future date" business rule by comparing to now.
  // ES: Cadena de fecha-hora del input datetime-local.
  //     El primer refine verifica que dayjs pueda analizarlo (protege contra inconsistencias del navegador).
  //     El segundo refine hace cumplir la regla de negocio "sin fecha futura" comparando con ahora.
  transactionDate: z
    .string()
    .min(1, 'Transaction date is required.')
    .refine((value) => dayjs(value).isValid(), 'Use a valid date and time.')
    .refine(
      (value) => !dayjs(value).isAfter(dayjs()),
      'Transaction date cannot be in the future.',
    ),
})

/** EN: TypeScript type inferred from the Zod schema — used as the form state type. / ES: Tipo TypeScript inferido del esquema Zod — usado como tipo de estado del formulario. */
export type TransactionFormValues = z.infer<typeof transactionFormSchema>

// ── Form Helpers ──────────────────────────────────────────────────────────────────────────

/**
 * EN: Computes the initial values for the form based on whether an existing transaction
 *     is being edited or a new one is being created.
 *     For new transactions, the date defaults to 5 minutes ago so it passes the
 *     "no future date" validation without the user needing to type anything.
 *
 * ES: Calcula los valores iniciales del formulario basado en si se está editando una
 *     transacción existente o creando una nueva.
 *     Para transacciones nuevas, la fecha predetermina a 5 minutos atrás para que pase
 *     la validación de "sin fecha futura" sin que el usuario necesite escribir nada.
 *
 * @param transaction - existing transaction for edit mode, or null for create mode
 *                     / transacción existente para modo edición, o null para modo creación
 * @returns initial form values object / objeto de valores iniciales del formulario
 */
export const getInitialFormValues = (
  transaction: Transaction | null,
): TransactionFormValues => ({
  amountInPesos: transaction?.amountInPesos ?? 0,
  merchant: transaction?.merchant ?? '',
  customerName: transaction?.customerName ?? '',
  // EN: Format the existing date back to the datetime-local input format (YYYY-MM-DDTHH:mm).
  //     Default to 5 minutes ago so a new form doesn't immediately fail the future date check.
  // ES: Formateamos la fecha existente de vuelta al formato del input datetime-local (YYYY-MM-DDTHH:mm).
  //     Predeterminamos a 5 minutos atrás para que un formulario nuevo no falle inmediatamente la verificación de fecha futura.
  transactionDate: transaction
    ? dayjs(transaction.transactionDate).format('YYYY-MM-DDTHH:mm')
    : dayjs().subtract(5, 'minute').format('YYYY-MM-DDTHH:mm'),
})

/**
 * EN: Converts validated form values into the API payload format.
 *     Normalizes whitespace in text fields (mirrors the backend service's sanitizeText).
 *     Formats the date to 'YYYY-MM-DDTHH:mm:ss' as required by the backend
 *     (the 'T' separator and seconds component are needed for LocalDateTime deserialization).
 *
 * ES: Convierte los valores de formulario validados al formato de payload de API.
 *     Normaliza los espacios en campos de texto (refleja el sanitizeText del servicio backend).
 *     Formatea la fecha a 'YYYY-MM-DDTHH:mm:ss' como requiere el backend
 *     (el separador 'T' y el componente de segundos son necesarios para la deserialización LocalDateTime).
 *
 * @param values - validated form values / valores de formulario validados
 * @returns API-ready payload / payload listo para la API
 */
export const toPayload = (
  values: TransactionFormValues,
): TransactionPayload => ({
  amountInPesos: values.amountInPesos,
  // EN: Collapse internal whitespace to a single space (matches server-side sanitizeText behavior).
  // ES: Colapsamos los espacios internos a un solo espacio (coincide con el comportamiento de sanitizeText del servidor).
  merchant: values.merchant.trim().replace(/\s+/g, ' '),
  customerName: values.customerName.trim().replace(/\s+/g, ' '),
  // EN: Include seconds component so the Spring Boot @PastOrPresent constraint parses correctly.
  // ES: Incluimos el componente de segundos para que la restricción @PastOrPresent de Spring Boot analice correctamente.
  transactionDate: dayjs(values.transactionDate).format('YYYY-MM-DDTHH:mm:ss'),
})
