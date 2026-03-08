import { useEffect, useId, type ReactNode } from 'react'
import { createPortal } from 'react-dom'

type ModalProps = {
  children: ReactNode
  description?: string
  isOpen: boolean
  onClose: () => void
  title: string
}

export function Modal({
  children,
  description,
  isOpen,
  onClose,
  title,
}: ModalProps) {
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    if (!isOpen) {
      return
    }

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

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen, onClose])

  if (!isOpen) {
    return null
  }

  return createPortal(
    <div
      aria-hidden="true"
      className="modal-root"
      onClick={onClose}
      role="presentation"
    >
      <div className="modal-backdrop" />

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
            <p className="eyebrow">Create workflow</p>
            <h2 id={titleId}>{title}</h2>
            {description ? (
              <p className="modal__description" id={descriptionId}>
                {description}
              </p>
            ) : null}
          </div>

          <button
            aria-label="Close modal"
            className="ghost-button modal__close"
            onClick={onClose}
            type="button"
          >
            Close
          </button>
        </div>

        <div className="modal__body">{children}</div>
      </section>
    </div>,
    document.body,
  )
}
