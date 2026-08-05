import React, { useEffect } from "react";

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
    <div style={{ position: "fixed", inset: 0, zIndex: 10000, display: "flex", alignItems: "center", justifyContent: "center", padding: "1rem", background: "rgba(15,23,42,.72)" }}>
      <section role="dialog" aria-modal="true" aria-labelledby="confirmation-title" style={{ width: "min(520px, 100%)", padding: "1.5rem", borderRadius: "12px", background: "white", boxShadow: "0 24px 60px rgba(0,0,0,.3)" }}>
        <h2 id="confirmation-title" style={{ marginTop: 0 }}>{title}</h2>
        <div style={{ lineHeight: 1.55 }}>{children}</div>
        <div style={{ display: "flex", justifyContent: "flex-end", gap: ".75rem", marginTop: "1.5rem" }}>
          <button type="button" onClick={onClose} disabled={busy}>Vazgeç</button>
          <button type="button" onClick={onConfirm} disabled={busy || confirmDisabled} style={{ padding: ".65rem 1rem", border: 0, borderRadius: "6px", background: destructive ? "#b91c1c" : "#0369a1", color: "white", fontWeight: 700 }}>
            {busy ? "İşleniyor…" : confirmLabel}
          </button>
        </div>
      </section>
    </div>
  );
};
