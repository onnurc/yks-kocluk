import React, { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import type { LegalDocument } from "./legalTypes";

interface LegalDocumentViewerProps {
  document?: LegalDocument;
  label: string;
  loading?: boolean;
  error?: Error | null;
  onRetry?: () => void;
}

export const LegalDocumentViewer: React.FC<LegalDocumentViewerProps> = ({
  document,
  label,
  loading = false,
  error = null,
  onRetry,
}) => {
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [open]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        style={{
          border: 0,
          padding: 0,
          background: "transparent",
          color: "#0369a1",
          textDecoration: "underline",
          cursor: loading ? "wait" : "pointer",
          font: "inherit",
        }}
      >
        {label}
      </button>

      {open && createPortal(
        <div
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) setOpen(false);
          }}
          style={{
            position: "fixed",
            inset: 0,
            zIndex: 10000,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "1rem",
            background: "rgba(15, 23, 42, 0.72)",
          }}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="legal-document-title"
            style={{
              width: "min(720px, 100%)",
              maxHeight: "88vh",
              display: "flex",
              flexDirection: "column",
              borderRadius: "12px",
              background: "#fff",
              boxShadow: "0 24px 60px rgba(0,0,0,.3)",
            }}
          >
            <header style={{ display: "flex", justifyContent: "space-between", gap: "1rem", padding: "1.25rem", borderBottom: "1px solid #e2e8f0" }}>
              <div>
                <h2 id="legal-document-title" style={{ margin: 0, fontSize: "1.25rem" }}>
                  {document?.title || label}
                </h2>
                {document && <small style={{ color: "#64748b" }}>Sürüm {document.version}</small>}
              </div>
              <button type="button" aria-label="Hukuki metni kapat" onClick={() => setOpen(false)} style={{ border: 0, background: "transparent", fontSize: "1.5rem", cursor: "pointer" }}>
                ×
              </button>
            </header>

            <div style={{ padding: "1.25rem", overflowY: "auto", whiteSpace: "pre-wrap", lineHeight: 1.65, color: "#334155" }}>
              {loading && <p role="status">Hukuki metin yükleniyor…</p>}
              {!loading && error && (
                <div role="alert">
                  <p>Hukuki metin yüklenemedi. Lütfen yeniden deneyin.</p>
                  {onRetry && <button type="button" onClick={onRetry}>Yeniden Dene</button>}
                </div>
              )}
              {!loading && !error && document && document.content}
              {!loading && !error && !document && <p>Hukuki metin bulunamadı.</p>}
            </div>
          </section>
        </div>,
        window.document.body
      )}
    </>
  );
};
