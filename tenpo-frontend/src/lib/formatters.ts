/**
 * EN: Shared formatting utilities used across multiple UI components.
 *     Centralizing these formatters prevents duplication (DRY principle) and ensures
 *     that every part of the UI uses the exact same locale and options, making
 *     currency and date representation consistent application-wide.
 *
 * ES: Utilidades de formato compartidas usadas en múltiples componentes de UI.
 *     Centralizar estos formateadores previene la duplicación (principio DRY) y garantiza
 *     que cada parte de la UI use exactamente el mismo locale y opciones, haciendo que
 *     la representación de moneda y fechas sea consistente en toda la aplicación.
 *
 * DRY fix:
 *   Previously, the Intl.NumberFormat('es-CL') instance was defined independently in
 *   both App.tsx and TransactionList.tsx. A future divergence (e.g. changing maximumFractionDigits)
 *   would require updating two files. Extracting it here makes the change atomic.
 */

// ── Currency Formatter ────────────────────────────────────────────────────────────────────

/**
 * EN: Formats an integer amount in Chilean Pesos (CLP) using the es-CL locale.
 *     Examples:
 *       formatCLP(15000)  → "$15.000"
 *       formatCLP(0)      → "$0"
 *       formatCLP(1000000) → "$1.000.000"
 *
 *     maximumFractionDigits: 0 → CLP has no decimal cents (ISO 4217 exponent 0).
 *
 *     The formatter is created once (module-level constant) and reused on every call,
 *     avoiding the cost of constructing Intl.NumberFormat on each render.
 *
 * ES: Formatea un monto entero en Pesos Chilenos (CLP) usando el locale es-CL.
 *     Ejemplos:
 *       formatCLP(15000)  → "$15.000"
 *       formatCLP(0)      → "$0"
 *
 *     maximumFractionDigits: 0 → CLP no tiene centavos decimales (exponente ISO 4217 = 0).
 *
 *     El formateador se crea una vez (constante a nivel de módulo) y se reutiliza en cada llamada,
 *     evitando el costo de construir Intl.NumberFormat en cada renderización.
 */
const clpFormatter = new Intl.NumberFormat('es-CL', {
  // EN: 'currency' style prepends the currency symbol (e.g. "$").
  // ES: El estilo 'currency' antepone el símbolo de moneda (ej. "$").
  style: 'currency',
  currency: 'CLP',
  // EN: CLP has no sub-unit (centavos are not used), so decimals are suppressed.
  // ES: CLP no tiene subunidad (los centavos no se usan), por lo que los decimales se suprimen.
  maximumFractionDigits: 0,
})

/**
 * EN: Formats a CLP amount into a locale-aware string.
 *
 * ES: Formatea un monto CLP en una cadena sensible al locale.
 *
 * @param amount - integer amount in Chilean pesos / monto entero en pesos chilenos
 * @returns formatted currency string / cadena de moneda formateada
 */
export function formatCLP(amount: number): string {
  return clpFormatter.format(amount)
}
