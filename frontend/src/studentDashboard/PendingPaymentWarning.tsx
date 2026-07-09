import React from "react";
import type { DashboardPayment } from "./studentDashboardTypes";
import { PaymentStatusBadge } from "./PaymentStatusBadge";

interface PendingPaymentWarningProps {
  payment: DashboardPayment | null;
}

export const PendingPaymentWarning: React.FC<PendingPaymentWarningProps> = ({ payment }) => {
  return (
    <div style={{ padding: "1.5rem", marginBottom: "1.5rem", border: "1px solid #ffeeba", borderRadius: "8px", backgroundColor: "#fff3cd", color: "#856404" }}>
      <h3 style={{ margin: "0 0 0.5rem 0" }}>⚠️ Ödeme İşleminiz Bekleniyor</h3>
      <p style={{ margin: "0 0 1rem 0" }}>
        Koçluk paketinizin ve görüşme haklarınızın aktif olabilmesi için ödeme işleminin tamamlanması gerekmektedir.
      </p>
      {payment && (
        <div style={{ fontSize: "0.9rem", borderTop: "1px solid #ffe8a1", paddingTop: "0.75rem" }}>
          <p style={{ margin: "0.25rem 0" }}><strong>Ödeme Tutarı:</strong> {payment.amount} TRY</p>
          <p style={{ margin: "0.25rem 0" }}>
            <strong>İşlem Durumu:</strong> <PaymentStatusBadge status={payment.status} />
          </p>
          <p style={{ margin: "0.25rem 0", fontSize: "0.8rem", color: "#b58900" }}>
            * Geliştirici modundaysanız, ödeme durumunu simüle etmek için stub endpoint'lerini tetikleyebilirsiniz.
          </p>
        </div>
      )}
    </div>
  );
};
