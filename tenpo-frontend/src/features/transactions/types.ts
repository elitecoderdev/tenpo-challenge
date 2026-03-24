/**
 * EN: TypeScript type definitions for the transactions feature.
 *     These types mirror the API contract defined by the Spring Boot backend DTOs
 *     (TransactionResponse, TransactionRequest, ApiError, ApiFieldError).
 *     Keeping them in a dedicated file makes them importable by queries, schema,
 *     components, and tests without circular dependencies.
 *
 * ES: Definiciones de tipos TypeScript para la característica de transacciones.
 *     Estos tipos reflejan el contrato de API definido por los DTOs del backend Spring Boot
 *     (TransactionResponse, TransactionRequest, ApiError, ApiFieldError).
 *     Mantenerlos en un archivo dedicado los hace importables por consultas, esquemas,
 *     componentes y tests sin dependencias circulares.
 */

// ── Domain Types ──────────────────────────────────────────────────────────────────────────

/**
 * EN: Mirrors the TransactionResponse DTO from the backend.
 *     All fields are non-optional because the API always returns them for valid transactions.
 *     'transactionDate' is a string here (ISO-8601) rather than Date to avoid
 *     potential timezone shift issues during JSON deserialization — dayjs handles formatting.
 *
 * ES: Refleja el DTO TransactionResponse del backend.
 *     Todos los campos son no opcionales porque la API siempre los devuelve para transacciones válidas.
 *     'transactionDate' es string aquí (ISO-8601) en lugar de Date para evitar posibles problemas
 *     de cambio de zona horaria durante la deserialización JSON — dayjs maneja el formateo.
 */
export type Transaction = {
  /** EN: Database-generated surrogate key. / ES: Clave sustituta generada por la base de datos. */
  id: number
  /** EN: Amount in Chilean pesos (integer). / ES: Monto en pesos chilenos (entero). */
  amountInPesos: number
  /** EN: Merchant or business category name. / ES: Nombre del comercio o categoría del giro. */
  merchant: string
  /** EN: Tenpista display name. / ES: Nombre visible del Tenpista. */
  customerName: string
  /** EN: ISO-8601 transaction timestamp. / ES: Timestamp de transacción ISO-8601. */
  transactionDate: string
}

/**
 * EN: Payload type for create and update API calls.
 *     Omits 'id' because the id is assigned by the backend after insertion.
 *     Derived from Transaction using TypeScript's built-in Omit utility type (DRY).
 *
 * ES: Tipo de payload para llamadas de API de creación y actualización.
 *     Omite 'id' porque el id es asignado por el backend después de la inserción.
 *     Derivado de Transaction usando el tipo utilitario Omit de TypeScript (DRY).
 */
export type TransactionPayload = Omit<Transaction, 'id'>

// ── API Error Types ───────────────────────────────────────────────────────────────────────

/**
 * EN: Mirrors the ApiFieldError record from the backend.
 *     Used by the form to display inline validation messages next to the failing field.
 *
 * ES: Refleja el registro ApiFieldError del backend.
 *     Usado por el formulario para mostrar mensajes de validación inline junto al campo fallido.
 */
export type ApiFieldError = {
  /** EN: Name of the field that failed validation (matches the DTO field name). / ES: Nombre del campo que falló la validación (coincide con el nombre del campo DTO). */
  field: string
  /** EN: Human-readable constraint violation message. / ES: Mensaje de violación de restricción legible por humanos. */
  message: string
}

/**
 * EN: Mirrors the ApiError record from the backend.
 *     This is the structured error shape returned for all non-2xx responses.
 *     The frontend normalizes all Axios errors into this shape so that
 *     every error rendering path receives a consistent object.
 *
 * ES: Refleja el registro ApiError del backend.
 *     Esta es la forma de error estructurado devuelto para todas las respuestas no-2xx.
 *     El frontend normaliza todos los errores Axios a esta forma para que
 *     cada ruta de renderización de error reciba un objeto consistente.
 */
export type ApiError = {
  /** EN: UTC timestamp when the error was generated. / ES: Timestamp UTC cuando se generó el error. */
  timestamp: string
  /** EN: Numeric HTTP status code. / ES: Código de estado HTTP numérico. */
  status: number
  /** EN: HTTP reason phrase (e.g. "Bad Request"). / ES: Frase de razón HTTP (ej. "Bad Request"). */
  error: string
  /** EN: Human-readable error description. / ES: Descripción de error legible por humanos. */
  message: string
  /** EN: Request path that triggered the error. / ES: Ruta de solicitud que desencadenó el error. */
  path: string
  /** EN: Per-field validation errors (empty array when not a validation error). / ES: Errores de validación por campo (arreglo vacío cuando no es error de validación). */
  fieldErrors: ApiFieldError[]
}
