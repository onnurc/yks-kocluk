import React, { useEffect } from "react";
import "./confirmation-modal.css";

interface ConfirmationModalProps {
  open: boolean;
  title: string;
  children: React.ReactNode;
  confirmLabel: string;
  confirmDisabled?: boolean;
  busy?: boolean;
  destructive?: boolean;
  onConfirm: () => void;
  onClose: () => void;
}

export const ConfirmationModal: React.FC<ConfirmationModalProps> = ({ open, title, children, confirmLabel, confirmDisabled, busy, destructive, onConfirm, onClose }) => {
  useEffect(() => {
    if (!open) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [busy, onClose, open]);

  if (!open) return null;
  return (
    <div className="confirmation-modal__backdrop">
      <section className="confirmation-modal" role="dialog" aria-modal="true" aria-labelledby="confirmation-title" aria-busy={busy}>
        <h2 id="confirmation-title">{title}</h2>
        <div className="confirmation-modal__content">{children}</div>
        <div className="confirmation-modal__actions">
          <button className="confirmation-modal__cancel" type="button" onClick={onClose} disabled={busy}>Vazgeç</button>
          <button className={`confirmation-modal__confirm${destructive ? " confirmation-modal__confirm--destructive" : ""}`} type="button" onClick={onConfirm} disabled={busy || confirmDisabled}>
            {busy ? "İşleniyor…" : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
};
