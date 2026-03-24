import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

/**
 * EN: Props for ErrorBoundary.
 * ES: Props para ErrorBoundary.
 */
type ErrorBoundaryProps = {
  /** EN: The subtree to protect. / ES: El subárbol a proteger. */
  children: ReactNode
  /** EN: Optional fallback UI. Defaults to a full-page error message. / ES: UI de reserva opcional. Por defecto muestra un mensaje de error de página completa. */
  fallback?: ReactNode
}

type ErrorBoundaryState = {
  hasError: boolean
  message: string
}

/**
 * EN: Class-based error boundary that catches render-time errors in its child tree
 *     and renders a fallback UI instead of crashing the whole application.
 *
 *     React error boundaries must be class components — this is a React constraint.
 *     Hooks cannot implement componentDidCatch.
 *
 *     Placed at the application root (main.tsx) so any unhandled render error
 *     surfaces a readable message rather than a blank white screen.
 *
 * ES: Límite de error basado en clase que captura errores en tiempo de renderizado
 *     en su árbol de hijos y renderiza una UI de reserva en lugar de crashear toda la aplicación.
 *
 *     Los error boundaries de React deben ser componentes de clase — esta es una restricción de React.
 *     Los hooks no pueden implementar componentDidCatch.
 *
 *     Se coloca en la raíz de la aplicación (main.tsx) para que cualquier error de renderizado
 *     no manejado muestre un mensaje legible en lugar de una pantalla blanca en blanco.
 *
 * Design — SOLID:
 *   SRP : Handles only uncaught render errors; mutations/network errors are handled by React Query.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false, message: '' }
  }

  /**
   * EN: Called when a descendant throws during render. Updates state to show fallback UI.
   *     Runs during the "render" phase — do not produce side effects here.
   *
   * ES: Se llama cuando un descendiente lanza durante el renderizado. Actualiza el estado
   *     para mostrar la UI de reserva. Se ejecuta durante la fase de "render" — no producir
   *     efectos secundarios aquí.
   */
  static getDerivedStateFromError(error: unknown): ErrorBoundaryState {
    const message =
      error instanceof Error ? error.message : 'An unexpected render error occurred.'
    return { hasError: true, message }
  }

  /**
   * EN: Called after an error is caught. Suitable for logging to an error reporting service.
   *     Runs during the "commit" phase — side effects are safe here.
   *
   * ES: Se llama después de que se captura un error. Adecuado para registrar en un servicio
   *     de reporte de errores. Se ejecuta durante la fase de "commit" — los efectos secundarios
   *     son seguros aquí.
   */
  componentDidCatch(error: unknown, info: ErrorInfo): void {
    // EN: In production, replace console.error with a real error reporting service
    //     (e.g. Sentry, Datadog RUM) so the team is alerted automatically.
    // ES: En producción, reemplazar console.error con un servicio real de reporte de errores
    //     (ej. Sentry, Datadog RUM) para que el equipo sea alertado automáticamente.
    console.error('[ErrorBoundary] Uncaught render error:', error, info.componentStack)
  }

  render() {
    if (this.state.hasError) {
      // EN: Render the consumer-provided fallback, or the built-in full-page error card.
      // ES: Renderizamos el fallback proporcionado por el consumidor, o la tarjeta de error de página completa incorporada.
      if (this.props.fallback) {
        return this.props.fallback
      }

      return (
        <div
          role="alert"
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            minHeight: '100dvh',
            padding: '2rem',
            textAlign: 'center',
            gap: '0.75rem',
          }}
        >
          <strong style={{ fontSize: '1.25rem' }}>Something went wrong</strong>
          {/* EN: Show the error message in development; hide in production if preferred. / ES: Mostramos el mensaje de error en desarrollo; ocultamos en producción si se prefiere. */}
          <p style={{ color: 'var(--color-muted, #888)', maxWidth: '40ch' }}>
            {this.state.message}
          </p>
          <button
            onClick={() => this.setState({ hasError: false, message: '' })}
            style={{ marginTop: '1rem' }}
            type="button"
          >
            Try again
          </button>
        </div>
      )
    }

    return this.props.children
  }
}
