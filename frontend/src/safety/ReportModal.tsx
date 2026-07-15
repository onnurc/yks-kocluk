import React, { useState } from "react";
import { safetyApi } from "./safetyApi";
import { ApiError } from "../api/ApiError";

interface ReportModalProps {
  targetType: "USER" | "CONVERSATION" | "MESSAGE";
  targetId: number;
  onClose: () => void;
  onSuccess?: () => void;
}

export const ReportModal: React.FC<ReportModalProps> = ({
  targetType,
  targetId,
  onClose,
  onSuccess,
}) => {
  const [reason, setReason] = useState("Kaba / Uygunsuz Davranış");
  const [details, setDetails] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const getTitle = () => {
    switch (targetType) {
      case "USER":
        return "Kullanıcıyı Bildir";
      case "CONVERSATION":
        return "Konuşmayı Bildir";
      case "MESSAGE":
        return "Mesajı Bildir";
    }
  };

  const getLabel = () => {
    switch (targetType) {
      case "USER":
        return "Bu kullanıcı hakkında şikayet gerekçeniz:";
      case "CONVERSATION":
        return "Bu konuşma hakkında şikayet gerekçeniz:";
      case "MESSAGE":
        return "Bu mesaj hakkında şikayet gerekçeniz:";
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!reason || isSubmitting) return;

    setIsSubmitting(true);
    setErrorMsg(null);
    setSuccessMsg(null);

    try {
      await safetyApi.createReport(targetType, targetId, reason, details);
      setSuccessMsg("Bildiriminiz başarıyla iletildi. Teşekkür ederiz.");
      setTimeout(() => {
        if (onSuccess) onSuccess();
        onClose();
      }, 2000);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMsg(err.detail || "Bildirim gönderilirken bir hata oluştu.");
      } else {
        setErrorMsg("Sunucuya ulaşılamadı. Lütfen tekrar deneyin.");
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="report-modal-title"
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        backgroundColor: "rgba(0, 0, 0, 0.6)",
        backdropFilter: "blur(4px)",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        zIndex: 9999,
        padding: "1rem",
      }}
    >
      <div
        style={{
          backgroundColor: "#ffffff",
          color: "#212529",
          borderRadius: "12px",
          width: "100%",
          maxWidth: "500px",
          padding: "2rem",
          boxShadow: "0 10px 25px rgba(0, 0, 0, 0.2)",
          display: "flex",
          flexDirection: "column",
          fontFamily: "sans-serif",
        }}
      >
        <h2 id="report-modal-title" style={{ margin: "0 0 1.5rem 0", color: "#d9534f", fontSize: "1.3rem", fontWeight: "700" }}>
          {getTitle()}
        </h2>

        {successMsg ? (
          <div
            style={{
              padding: "1rem",
              backgroundColor: "#d4edda",
              border: "1px solid #c3e6cb",
              borderRadius: "8px",
              color: "#155724",
              fontSize: "0.95rem",
              textAlign: "center",
              margin: "1rem 0",
            }}
          >
            {successMsg}
          </div>
        ) : (
          <form onSubmit={handleSubmit}>
            {errorMsg && (
              <div
                style={{
                  padding: "1rem",
                  backgroundColor: "#f8d7da",
                  border: "1px solid #f5c6cb",
                  borderRadius: "8px",
                  color: "#721c24",
                  fontSize: "0.9rem",
                  marginBottom: "1rem",
                }}
              >
                {errorMsg}
              </div>
            )}

            <div style={{ marginBottom: "1rem" }}>
              <label htmlFor="report-reason" style={{ display: "block", marginBottom: "0.5rem", fontWeight: "600", fontSize: "0.9rem" }}>
                {getLabel()}
              </label>
              <select
                id="report-reason"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                disabled={isSubmitting}
                style={{
                  width: "100%",
                  padding: "0.6rem",
                  borderRadius: "6px",
                  border: "1px solid #ccc",
                  fontSize: "0.95rem",
                  backgroundColor: "#fff",
                }}
              >
                <option value="Kaba / Uygunsuz Davranış">Kaba / Uygunsuz Davranış</option>
                <option value="Spam veya Reklam">Spam veya Reklam</option>
                <option value="Taciz veya Tehdit">Taciz veya Tehdit</option>
                <option value="Sözleşme / Kural İhlali">Sözleşme / Kural İhlali</option>
                <option value="Diğer">Diğer</option>
              </select>
            </div>

            <div style={{ marginBottom: "1.5rem" }}>
              <label htmlFor="report-details" style={{ display: "block", marginBottom: "0.5rem", fontWeight: "600", fontSize: "0.9rem" }}>
                Detaylar (İsteğe bağlı, en fazla 1000 karakter):
              </label>
              <textarea
                id="report-details"
                value={details}
                onChange={(e) => setDetails(e.target.value.slice(0, 1000))}
                disabled={isSubmitting}
                rows={4}
                placeholder="Lütfen şikayetiniz hakkında ek detayları paylaşın..."
                style={{
                  width: "100%",
                  padding: "0.6rem",
                  borderRadius: "6px",
                  border: "1px solid #ccc",
                  fontSize: "0.95rem",
                  resize: "vertical",
                }}
              />
              <div style={{ textAlign: "right", fontSize: "0.8rem", color: "#6c757d", marginTop: "0.25rem" }}>
                {details.length}/1000
              </div>
            </div>

            <div style={{ display: "flex", justifyContent: "flex-end", gap: "1rem" }}>
              <button
                type="button"
                onClick={onClose}
                disabled={isSubmitting}
                style={{
                  padding: "0.5rem 1.2rem",
                  backgroundColor: "#e2e8f0",
                  color: "#4a5568",
                  border: "none",
                  borderRadius: "6px",
                  cursor: isSubmitting ? "not-allowed" : "pointer",
                  fontSize: "0.9rem",
                  fontWeight: "600",
                }}
              >
                Vazgeç
              </button>
              <button
                type="submit"
                disabled={isSubmitting}
                style={{
                  padding: "0.5rem 1.5rem",
                  backgroundColor: "#d9534f",
                  color: "#fff",
                  border: "none",
                  borderRadius: "6px",
                  cursor: isSubmitting ? "not-allowed" : "pointer",
                  fontSize: "0.9rem",
                  fontWeight: "600",
                }}
              >
                {isSubmitting ? "Gönderiliyor..." : "Bildir"}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};
