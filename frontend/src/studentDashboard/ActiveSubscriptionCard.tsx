import React from "react";
import type { DashboardSubscription, DashboardPayment } from "./studentDashboardTypes";
import { SubscriptionStatusBadge } from "./SubscriptionStatusBadge";
import { PaymentStatusBadge } from "./PaymentStatusBadge";

interface ActiveSubscriptionCardProps {
  subscription: DashboardSubscription;
  payment: DashboardPayment | null;
}

export const ActiveSubscriptionCard: React.FC<ActiveSubscriptionCardProps> = ({ subscription, payment }) => {
  const formatDate = (dateStr: string) => {
    if (!dateStr) return "-";
    try {
      return new Date(dateStr).toLocaleDateString("tr-TR", {
        year: "numeric",
        month: "long",
        day: "numeric",
      });
    } catch {
      return dateStr;
    }
  };

  return (
    <div style={{ padding: "1.5rem", border: "1px solid #c3e6cb", borderRadius: "8px", backgroundColor: "#d4edda", color: "#155724", marginBottom: "1.5rem" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
        <h3 style={{ margin: 0 }}>✅ Aktif Koçluk Aboneliği</h3>
        <SubscriptionStatusBadge status={subscription.status} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem", fontSize: "0.95rem" }}>
        <div>
          <p style={{ margin: "0.5rem 0" }}><strong>Koçunuz:</strong> {subscription.coachName}</p>
          <p style={{ margin: "0.5rem 0" }}><strong>Paket:</strong> {subscription.packageName}</p>
          <p style={{ margin: "0.5rem 0" }}><strong>Otomatik Yenileme:</strong> {subscription.autoRenew ? "Aktif" : "Pasif"}</p>
        </div>
        <div>
          <p style={{ margin: "0.5rem 0" }}><strong>Başlangıç Tarihi:</strong> {formatDate(subscription.startAt)}</p>
          <p style={{ margin: "0.5rem 0" }}><strong>Sonraki Ödeme Tarihi:</strong> {formatDate(subscription.endAt)}</p>
          {payment && (
            <p style={{ margin: "0.5rem 0" }}>
              <strong>Son Ödeme:</strong> {payment.amount} TRY (<PaymentStatusBadge status={payment.status} />)
            </p>
          )}
        </div>
      </div>

      {!subscription.autoRenew && (
        <div style={{ marginTop: "1rem", padding: "0.75rem", border: "1px solid #ffeeba", borderRadius: "4px", backgroundColor: "#fff3cd", color: "#856404", fontSize: "0.85rem" }}>
          Otomatik yenileme kapatılmıştır. Hizmete olan erişiminiz <strong>{formatDate(subscription.endAt)}</strong> tarihine kadar devam edecek ve bu tarihten sonra yenilenmeyecektir.
        </div>
      )}
    </div>
  );
};
