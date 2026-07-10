import React, { useEffect, useState } from "react";
import { subscriptionManagementApi } from "./subscriptionManagementApi";
import { ApiError } from "../api/ApiError";

interface CancelRenewalModalProps {
  subscriptionId: number;
  formattedEndAt: string;
  onClose: () => void;
  onSuccess: (successMsg: string) => void;
}

export const CancelRenewalModal: React.FC<CancelRenewalModalProps> = ({
  subscriptionId,
  formattedEndAt,
  onClose,
  onSuccess,
}) => {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Handle Escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape" && !submitting) {
        onClose();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [submitting, onClose]);

  const handleConfirm = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;

    setSubmitting(true);
    setError(null);

    try {
      await subscriptionManagementApi.cancelRenewal(subscriptionId);
      onSuccess(`Otomatik yenileme kapatıldı. Erişiminiz ${formattedEndAt} tarihine kadar devam edecek.`);
      onClose();
    } catch (err: any) {
      if (err instanceof ApiError) {
        if (err.code === "SUBSCRIPTION_NOT_CANCELLABLE") {
          setError("Bu abonelik için otomatik yenileme değiştirilemez.");
        } else if (err.code === "SUBSCRIPTION_NOT_FOUND") {
          setError("Yönetilebilecek aktif bir abonelik bulunamadı.");
        } else if (err.code === "NOT_SUBSCRIPTION_OWNER") {
          setError("Bu işlem için yetkiniz bulunmuyor.");
        } else if (err.status === 403) {
          setError("Bu işlem için yetkiniz bulunmuyor.");
        } else {
          setError(err.message || "Bir hata oluştu.");
        }
      } else {
        setError("Sunucuya ulaşılamadı. Lütfen tekrar deneyin.");
      }
      setSubmitting(false);
    }
  };

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget && !submitting) {
      onClose();
    }
  };

  return (
    <div
      onClick={handleBackdropClick}
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        backgroundColor: "rgba(0, 0, 0, 0.5)",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        zIndex: 1000,
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="cancel-renewal-title"
        style={{
          backgroundColor: "white",
          borderRadius: "8px",
          padding: "2rem",
          width: "90%",
          maxWidth: "480px",
          boxShadow: "0 4px 12px rgba(0, 0, 0, 0.15)",
          color: "#333",
        }}
      >
        <h3 id="cancel-renewal-title" style={{ margin: "0 0 1rem 0", color: "#721c24" }}>
          Otomatik yenilemeyi kapat
        </h3>

        <p style={{ margin: "0 0 1.5rem 0", lineHeight: "1.5", fontSize: "0.95rem" }}>
          Bu işlem mevcut aboneliğinizi hemen sonlandırmaz. Koçluk, randevu ve mesajlaşma erişiminiz <strong>{formattedEndAt}</strong> tarihine kadar devam eder. Bu tarihten sonra yeni dönem için otomatik ödeme alınmaz.
        </p>

        {error && (
          <div
            style={{
              padding: "0.75rem",
              backgroundColor: "#f8d7da",
              border: "1px solid #f5c6cb",
              color: "#721c24",
              borderRadius: "4px",
              marginBottom: "1rem",
              fontSize: "0.9rem",
            }}
          >
            {error}
          </div>
        )}

        <div style={{ display: "flex", justifyContent: "flex-end", gap: "1rem" }}>
          <button
            type="button"
            onClick={onClose}
            disabled={submitting}
            style={{
              padding: "0.5rem 1rem",
              backgroundColor: "#e2e8f0",
              color: "#4a5568",
              border: "none",
              borderRadius: "4px",
              cursor: submitting ? "not-allowed" : "pointer",
              fontWeight: "bold",
            }}
          >
            Vazgeç
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={submitting}
            style={{
              padding: "0.5rem 1rem",
              backgroundColor: submitting ? "#f5c6cb" : "#dc3545",
              color: "white",
              border: "none",
              borderRadius: "4px",
              cursor: submitting ? "not-allowed" : "pointer",
              fontWeight: "bold",
            }}
          >
            {submitting ? "İşleniyor..." : "Yenilemeyi İptal Et"}
          </button>
        </div>
      </div>
    </div>
  );
};
