import React, { useEffect, useState } from "react";
import { ApiError } from "../api/ApiError";
import { subscriptionManagementApi } from "./subscriptionManagementApi";

interface CancelRenewalModalProps {
  subscriptionId: number;
  formattedEndAt: string;
  onClose: () => void;
  onSuccess: (successMsg: string) => void;
}

export const CancelRenewalModal: React.FC<CancelRenewalModalProps> = ({ subscriptionId, formattedEndAt, onClose, onSuccess }) => {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !submitting) onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [submitting, onClose]);

  const handleConfirm = async () => {
    if (submitting) return;
    setSubmitting(true);
    setError(null);

    try {
      await subscriptionManagementApi.cancelRenewal(subscriptionId);
      onSuccess(`Otomatik yenileme kapatıldı. Erişiminiz ${formattedEndAt} tarihine kadar devam edecek.`);
      onClose();
    } catch (caughtError: unknown) {
      if (caughtError instanceof ApiError) {
        if (caughtError.code === "SUBSCRIPTION_NOT_CANCELLABLE") {
          setError("Bu abonelik için otomatik yenileme değiştirilemez.");
        } else if (caughtError.code === "SUBSCRIPTION_NOT_FOUND") {
          setError("Yönetilebilecek aktif bir abonelik bulunamadı.");
        } else if (caughtError.code === "NOT_SUBSCRIPTION_OWNER" || caughtError.status === 403) {
          setError("Bu işlem için yetkiniz bulunmuyor.");
        } else {
          setError(caughtError.message || "İşlem tamamlanamadı. Lütfen tekrar deneyin.");
        }
      } else {
        setError("Sunucuya ulaşılamadı. Lütfen tekrar deneyin.");
      }
      setSubmitting(false);
    }
  };

  const handleBackdropClick = (event: React.MouseEvent) => {
    if (event.target === event.currentTarget && !submitting) onClose();
  };

  return (
    <div className="subscription-modal__backdrop" onClick={handleBackdropClick}>
      <div className="subscription-modal" role="dialog" aria-modal="true" aria-labelledby="cancel-renewal-title">
        <p className="subscription-modal__eyebrow">Abonelik işlemi</p>
        <h3 id="cancel-renewal-title">Otomatik yenilemeyi kapat</h3>
        <p>
          Bu işlem aboneliğinizi hemen sonlandırmaz. Koçluk, görüşme ve mesajlaşma erişiminiz <strong>{formattedEndAt}</strong> tarihine kadar devam eder; ardından otomatik ödeme alınmaz.
        </p>

        {error && <p className="subscription-modal__error" role="alert">{error}</p>}

        <div className="subscription-modal__actions">
          <button type="button" className="subscription-modal__cancel" onClick={onClose} disabled={submitting}>Vazgeç</button>
          <button type="button" className="subscription-modal__confirm" onClick={handleConfirm} disabled={submitting}>
            {submitting ? "İşleniyor…" : "Yenilemeyi İptal Et"}
          </button>
        </div>
      </div>
    </div>
  );
};
