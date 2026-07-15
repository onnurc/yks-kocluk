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
    } catch (err) {
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
    <div
      style={{
        padding: "1.5rem",
        marginBottom: "1.5rem",
        border: "1px solid #ffeeba",
        borderRadius: "8px",
        backgroundColor: "#fff3cd",
        color: "#856404",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", flexWrap: "wrap", gap: "1rem" }}>
        <div>
          <h3 style={{ margin: "0 0 0.5rem 0" }}>⚠️ Ödeme İşleminiz Bekleniyor</h3>
          <p style={{ margin: "0 0 1rem 0" }}>
            Koçluk paketinizin ve görüşme haklarınızın aktif olabilmesi için ödeme işleminin tamamlanması gerekmektedir.
          </p>
        </div>
        <button
          disabled={refreshing}
          onClick={handleManualRefresh}
          style={{
            padding: "0.5rem 1rem",
            backgroundColor: "#ffc107",
            color: "#212529",
            border: "1px solid #ffc107",
            borderRadius: "4px",
            fontWeight: "bold",
            cursor: refreshing ? "not-allowed" : "pointer",
            fontSize: "0.85rem",
          }}
        >
          {refreshing ? "Güncelleniyor..." : "Durumu Yenile"}
        </button>
      </div>

      {payment && (
        <div style={{ fontSize: "0.9rem", borderTop: "1px solid #ffe8a1", paddingTop: "0.75rem", display: "flex", justifyContent: "space-between", alignItems: "flex-end" }}>
          <div>
            <p style={{ margin: "0.25rem 0" }}><strong>Ödeme Tutarı:</strong> {payment.amount} TRY</p>
            <p style={{ margin: "0.25rem 0" }}>
              <strong>İşlem Durumu:</strong> <PaymentStatusBadge status={payment.status} />
            </p>
            {payment.status === "PENDING" && pollCount < maxPolls && (
              <p style={{ margin: "0.25rem 0", fontSize: "0.75rem", color: "#856404", fontStyle: "italic" }}>
                * Ödeme durumu otomatik olarak güncelleniyor (Yenileme: {pollCount + 1}/{maxPolls})
              </p>
            )}
          </div>

          {isStubEnabled && payment.status === "PENDING" && (
            <button
              disabled={stubLoading}
              onClick={handleStubSuccess}
              style={{
                padding: "0.5rem 1rem",
                backgroundColor: "#28a745",
                color: "white",
                border: "none",
                borderRadius: "4px",
                fontWeight: "bold",
                cursor: stubLoading ? "not-allowed" : "pointer",
                fontSize: "0.85rem",
                boxShadow: "0 2px 4px rgba(40,167,69,0.2)",
              }}
            >
              {stubLoading ? "İşleniyor..." : "Local Test: Ödemeyi Başarılı Yap"}
            </button>
          )}
        </div>
      )}
    </div>
  );
};
