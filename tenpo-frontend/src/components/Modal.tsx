import { useEffect, useId, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

/**
 * EN: Props for the Modal component.
 *     'title' and 'isOpen' are always required; 'description' is optional.
 *     Accessibility: title is referenced by aria-labelledby; description by aria-describedby.
 *
 * ES: Props para el componente Modal.
 *     'title' e 'isOpen' siempre son requeridos; 'description' es opcional.
 *     Accesibilidad: title es referenciado por aria-labelledby; description por aria-describedby.
 */
type ModalProps = {
  /** EN: The modal's main content. / ES: El contenido principal del modal. */
  children: ReactNode
  /** EN: Optional description shown below the title, wired to aria-describedby. / ES: Descripción opcional mostrada debajo del título, conectada a aria-describedby. */
  description?: string
  /** EN: Controls whether the modal is visible. / ES: Controla si el modal es visible. */
  isOpen: boolean
  /** EN: Callback invoked when the user closes the modal (backdrop click, Escape key, close button). / ES: Callback invocado cuando el usuario cierra el modal (clic en backdrop, tecla Escape, botón cerrar). */
  onClose: () => void
  /** EN: Accessible title rendered in the modal header, wired to aria-labelledby. / ES: Título accesible renderizado en el encabezado del modal, conectado a aria-labelledby. */
  title: string
}

/**
 * EN: Generic modal component that renders its content via React Portal.
 *     Using createPortal ensures the modal appears at document.body level, escaping
 *     any stacking context (z-index) issues in the component tree.
 *
 *     Accessibility features implemented:
 *       - role="dialog" with aria-modal="true" (AT announces the modal context)
 *       - aria-labelledby / aria-describedby (screen reader reads title and description)
 *       - Escape key closes the modal (keyboard user expectation per ARIA APG)
 *       - Background scroll locked while modal is open (prevents confusing scroll behavior)
 *       - Click on the backdrop closes the modal (pointer user expectation)
 *       - stopPropagation on the modal panel prevents backdrop click from firing on panel interactions
 *
 * ES: Componente modal genérico que renderiza su contenido via React Portal.
 *     Usar createPortal asegura que el modal aparezca al nivel de document.body, escapando
 *     cualquier problema de contexto de apilamiento (z-index) en el árbol de componentes.
 *
 *     Características de accesibilidad implementadas:
 *       - role="dialog" con aria-modal="true" (los AT anuncian el contexto modal)
 *       - aria-labelledby / aria-describedby (lector de pantalla lee título y descripción)
 *       - La tecla Escape cierra el modal (expectativa del usuario de teclado según ARIA APG)
 *       - Scroll de fondo bloqueado mientras el modal está abierto (previene comportamiento de scroll confuso)
 *       - Clic en el backdrop cierra el modal (expectativa del usuario con puntero)
 */
export function Modal({
  children,
  description,
  isOpen,
  onClose,
  title,
}: ModalProps) {
  // EN: Generate unique IDs for aria-labelledby and aria-describedby to avoid ID collisions
  //     when multiple modals are mounted simultaneously (though this app uses only one at a time).
  // ES: Generamos IDs únicos para aria-labelledby y aria-describedby para evitar colisiones de ID
  //     cuando múltiples modales están montados simultáneamente (aunque esta app usa solo uno a la vez).
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    // EN: Skip the effect when the modal is closed to avoid interfering with page scroll
    //     or attaching unnecessary event listeners.
    // ES: Omitimos el efecto cuando el modal está cerrado para evitar interferir con el scroll
    //     de la página o adjuntar listeners de eventos innecesarios.
    if (!isOpen) {
      return
    }

    // EN: Capture the existing overflow value so we can restore it exactly on cleanup.
    //     This handles nested modal scenarios or cases where the page already had overflow:hidden.
    // ES: Capturamos el valor de overflow existente para restaurarlo exactamente en la limpieza.
    //     Esto maneja escenarios de modales anidados o casos donde la página ya tenía overflow:hidden.
    const previousOverflow = document.body.style.overflow

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose()
      }
    }

    // EN: Lock background scroll and let Escape close the modal for keyboard users.
    // ES: Bloqueamos el scroll del fondo y permitimos cerrar el modal con Escape para usuarios de teclado.
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', handleKeyDown)

    // EN: Cleanup function: restore scroll and remove the event listener when the modal closes
    //     or when the component unmounts. Returning the cleanup from useEffect is the correct
    //     pattern to avoid memory leaks and stale event handlers.
    // ES: Función de limpieza: restauramos el scroll y eliminamos el listener cuando el modal se cierra
    //     o cuando el componente se desmonta. Devolver la limpieza desde useEffect es el patrón correcto
    //     para evitar fugas de memoria y manejadores de eventos obsoletos.
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen, onClose])

  // EN: Return null when closed so the portal is not rendered to the DOM.
  //     This avoids any layout or focus side-effects from an invisible modal.
  // ES: Devolvemos null cuando está cerrado para que el portal no se renderice en el DOM.
  //     Esto evita efectos secundarios de layout o foco de un modal invisible.
  if (!isOpen) {
    return null
  }

  return createPortal(
    // EN: Outer wrapper captures backdrop clicks and acts as a focus boundary.
    //     aria-hidden="true" hides the backdrop from the accessibility tree; the
    //     inner section with role="dialog" is what screen readers announce.
    // ES: El wrapper externo captura los clics en el backdrop y actúa como límite de foco.
    //     aria-hidden="true" oculta el backdrop del árbol de accesibilidad; la sección
    //     interna con role="dialog" es lo que los lectores de pantalla anuncian.
    <div
      aria-hidden="true"
      className="modal-root"
      onClick={onClose}
      role="presentation"
    >
      {/* EN: Semi-transparent backdrop with blur effect. / ES: Backdrop semi-transparente con efecto de desenfoque. */}
      <div className="modal-backdrop" />

      {/* EN: The actual dialog panel. stopPropagation prevents clicks inside the panel
              from bubbling to the outer div and triggering onClose unintentionally.
          ES: El panel del diálogo real. stopPropagation previene que los clics dentro del panel
              se propaguen al div exterior y activen onClose sin querer. */}
      <section
        aria-describedby={description ? descriptionId : undefined}
        aria-labelledby={titleId}
        aria-modal="true"
        className="modal-panel"
        onClick={(event) => event.stopPropagation()}
        role="dialog"
      >
        <div className="modal__header">
          <div className="modal__copy">
            {/* EN: Eyebrow label provides context above the main title. / ES: La etiqueta eyebrow proporciona contexto sobre el título principal. */}
            <p className="eyebrow">Create workflow</p>
            <h2 id={titleId}>{title}</h2>
            {description ? (
              <p className="modal__description" id={descriptionId}>
                {description}
              </p>
            ) : null}
          </div>

          {/* EN: Close button wired to onClose; aria-label describes its action for screen readers. / ES: Botón cerrar conectado a onClose; aria-label describe su acción para lectores de pantalla. */}
          <button
            aria-label="Close modal"
            className="ghost-button modal__close"
            onClick={onClose}
            type="button"
          >
            Close
          </button>
        </div>

        {/* EN: Render children inside the modal body, keeping the component generic. / ES: Renderizamos children dentro del cuerpo del modal, manteniendo el componente genérico. */}
        <div className="modal__body">{children}</div>
      </section>
    </div>,
    // EN: Mount into document.body to escape parent z-index/overflow constraints.
    // ES: Montamos en document.body para escapar las restricciones de z-index/overflow del padre.
    document.body,
  )
}
