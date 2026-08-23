import React, { useEffect, useState, useRef } from "react";
import type { DashboardPayment } from "./studentDashboardTypes";
import { PaymentStatusBadge } from "./PaymentStatusBadge";
import { subscriptionCheckoutApi } from "../subscriptionCheckout/subscriptionCheckoutApi";

interface PendingPaymentWarningProps {
  payment: DashboardPayment | null;
  onRefresh: () => void;
}

export const PendingPaymentWarning: React.FC<PendingPaymentWarningProps> = ({
  payment,
  onRefresh,
}) => {
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [stubLoading, setStubLoading] = useState<boolean>(false);
  const [pollCount, setPollCount] = useState<number>(0);
  const maxPolls = 12;

  const onRefreshRef = useRef(onRefresh);
  useEffect(() => {
    onRefreshRef.current = onRefresh;
  }, [onRefresh]);

  // Bounded auto-polling trigger
  useEffect(() => {
    if (!payment || payment.status !== "PENDING" || pollCount >= maxPolls) {
      return;
    }

    const timer = setTimeout(() => {
      setPollCount((prev) => prev + 1);
      onRefreshRef.current();
    }, 5000);

    return () => clearTimeout(timer);
  }, [payment, pollCount]);

  const handleManualRefresh = async () => {
    if (refreshing) return;
    setRefreshing(true);
    try {
      await onRefresh();
    } finally {
      setRefreshing(false);
    }
  };

  const handleStubSuccess = async () => {
    if (!payment || stubLoading) return;
    setStubLoading(true);
    try {
      await subscriptionCheckoutApi.stubSucceed(payment.id);
      await onRefresh();
    } catch {
      alert("Stub ödeme onayı başarısız oldu.");
    } finally {
      setStubLoading(false);
    }
  };

  // Render stub success button in DEV mode with the environment flag set to true
  const isStubEnabled =
    import.meta.env.DEV &&
    import.meta.env.VITE_ENABLE_STUB_PAYMENT_SUCCESS === "true";

  return (
    <article className="pending-subscription" aria-labelledby="pending-subscription-title">
      <div className="pending-subscription__header">
        <div>
          <p className="pending-subscription__eyebrow">Abonelik durumu</p>
          <h3 id="pending-subscription-title">Ödeme İşleminiz Bekleniyor</h3>
          <p>
            Koçluk paketinizin ve görüşme haklarınızın aktif olabilmesi için ödeme işleminin tamamlanması gerekmektedir.
          </p>
        </div>
        <button
          className="pending-subscription__refresh"
          disabled={refreshing}
          onClick={handleManualRefresh}
        >
          {refreshing ? "Güncelleniyor…" : "Durumu Yenile"}
        </button>
      </div>

      {payment && (
        <div className="pending-subscription__details">
          <div>
            <p><strong>Ödeme Tutarı:</strong> {payment.amount} TRY</p>
            <p>
              <strong>İşlem Durumu:</strong> <PaymentStatusBadge status={payment.status} />
            </p>
            {payment.status === "PENDING" && pollCount < maxPolls && (
              <p className="pending-subscription__polling">
                Ödeme durumu otomatik olarak güncelleniyor ({pollCount + 1}/{maxPolls})
              </p>
            )}
          </div>

          {isStubEnabled && payment.status === "PENDING" && (
            <button
              className="pending-subscription__dev-action"
              disabled={stubLoading}
              onClick={handleStubSuccess}
            >
              {stubLoading ? "İşleniyor..." : "Local Test: Ödemeyi Başarılı Yap"}
            </button>
          )}
        </div>
      )}
    </article>
  );
};
