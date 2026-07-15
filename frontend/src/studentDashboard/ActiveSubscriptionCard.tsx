import React, { useState } from "react";
import { Link } from "react-router-dom";
import type { DashboardSubscription, DashboardPayment } from "./studentDashboardTypes";
import { SubscriptionStatusBadge } from "./SubscriptionStatusBadge";
import { PaymentStatusBadge } from "./PaymentStatusBadge";
import { CancelRenewalModal } from "../subscriptionManagement/CancelRenewalModal";

interface ActiveSubscriptionCardProps {
  subscription: DashboardSubscription;
  payment: DashboardPayment | null;
  onRefresh: () => void;
}

export const ActiveSubscriptionCard: React.FC<ActiveSubscriptionCardProps> = ({ subscription, payment, onRefresh }) => {
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const formatDate = (dateStr: string | null | undefined) => {
    if (!dateStr) return "mevcut dönem sonuna kadar";
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return "mevcut dönem sonuna kadar";
      return d.toLocaleDateString("tr-TR", {
        year: "numeric",
        month: "long",
        day: "numeric",
      });
    } catch {
      return "mevcut dönem sonuna kadar";
    }
  };

  const handleCancelSuccess = (msg: string) => {
    setSuccessMessage(msg);
    onRefresh();
  };

  return (
    <div style={{ padding: "1.5rem", border: "1px solid #c3e6cb", borderRadius: "8px", backgroundColor: "#d4edda", color: "#155724", marginBottom: "1.5rem" }}>
      {successMessage && (
        <div style={{ padding: "0.75rem", backgroundColor: "#d4edda", border: "1px solid #c3e6cb", color: "#155724", borderRadius: "4px", marginBottom: "1rem", fontSize: "0.9rem" }}>
          {successMessage}
        </div>
      )}

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

      <div style={{ marginTop: "1.5rem", display: "flex", gap: "1rem", flexWrap: "wrap", alignItems: "center" }}>
        <Link
          to={`/coaches/${subscription.coachId}`}
          style={{
            padding: "0.5rem 1rem",
            backgroundColor: "#28a745",
            color: "white",
            textDecoration: "none",
            borderRadius: "4px",
            fontWeight: "bold",
            fontSize: "0.9rem",
          }}
        >
          Koç Profili & Randevu Al
        </Link>
        <Link
          to="/bookings"
          style={{
            padding: "0.5rem 1rem",
            backgroundColor: "#007bff",
            color: "white",
            textDecoration: "none",
            borderRadius: "4px",
            fontWeight: "bold",
            fontSize: "0.9rem",
          }}
        >
          Randevularım
        </Link>
        <Link
          to="/messages"
          style={{
            padding: "0.5rem 1rem",
            backgroundColor: "#17a2b8",
            color: "white",
            textDecoration: "none",
            borderRadius: "4px",
            fontWeight: "bold",
            fontSize: "0.9rem",
          }}
        >
          Mesajlarım
        </Link>

        {subscription.autoRenew && (
          <button
            type="button"
            onClick={() => setShowCancelModal(true)}
            style={{
              padding: "0.5rem 1rem",
              backgroundColor: "#dc3545",
              color: "white",
              border: "none",
              borderRadius: "4px",
              fontWeight: "bold",
              fontSize: "0.9rem",
              cursor: "pointer",
            }}
          >
            Yenilemeyi İptal Et
          </button>
        )}
      </div>

      <div style={{ marginTop: "1.5rem", padding: "1rem", border: "1px solid #cce5ff", borderRadius: "6px", backgroundColor: "#e8f4fd", color: "#004085", fontSize: "0.85rem" }}>
        <h4 style={{ margin: "0 0 0.5rem 0", fontSize: "0.9rem" }}>ℹ️ Yenileme İptali ve Yönetici Sonlandırması Farkı</h4>
        <p style={{ margin: "0.25rem 0" }}><strong>Yenileme İptali:</strong> Bir sonraki dönemin otomatik olarak başlamasını engeller. Mevcut ücretli dönem sonuna kadar koçluk, randevu ve mesajlaşma erişimi devam eder.</p>
        <p style={{ margin: "0.25rem 0" }}><strong>Yönetici Sonlandırması:</strong> Platform yöneticisinin güvenlik, ihlal veya operasyonel nedenlerle aboneliği sonlandırmasıdır. Erişim mevcut dönemin bitmesi beklenmeden durdurulabilir.</p>
      </div>

      {showCancelModal && (
        <CancelRenewalModal
          subscriptionId={subscription.id}
          formattedEndAt={formatDate(subscription.endAt)}
          onClose={() => setShowCancelModal(false)}
          onSuccess={handleCancelSuccess}
        />
      )}
    </div>
  );
};
