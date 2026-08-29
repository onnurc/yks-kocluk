import React, { useState } from "react";
import { Link } from "react-router-dom";
import type { DashboardPayment, DashboardSubscription } from "./studentDashboardTypes";
import { SubscriptionStatusBadge } from "./SubscriptionStatusBadge";
import { PaymentStatusBadge } from "./PaymentStatusBadge";
import { CancelRenewalModal } from "../subscriptionManagement/CancelRenewalModal";
import { RefundRequestAction } from "../refunds/RefundRequestAction";

interface ActiveSubscriptionCardProps {
  subscription: DashboardSubscription;
  payment: DashboardPayment | null;
  onRefresh: () => void;
}

const formatDate = (dateValue: string | null | undefined) => {
  if (!dateValue) return "Mevcut dönem sonu";

  const date = new Date(dateValue);
  if (Number.isNaN(date.getTime())) return "Mevcut dönem sonu";

  return date.toLocaleDateString("tr-TR", {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
};

const formatAmount = (amount: number) => new Intl.NumberFormat("tr-TR", {
  style: "currency",
  currency: "TRY",
}).format(amount);

export const ActiveSubscriptionCard: React.FC<ActiveSubscriptionCardProps> = ({ subscription, payment, onRefresh }) => {
  const [showCancelModal, setShowCancelModal] = useState(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const handleCancelSuccess = (message: string) => {
    setSuccessMessage(message);
    onRefresh();
  };

  return (
    <article className="subscription-management" aria-labelledby="active-subscription-title">
      {successMessage && <p className="subscription-management__success" role="status">{successMessage}</p>}

      <header className="subscription-management__header">
        <div>
          <p className="subscription-management__eyebrow">Abonelik yönetimi</p>
          <h2 id="active-subscription-title">Aktif Koçluk Aboneliği</h2>
          <p>Koçluk paketinizin dönem ve yenileme bilgilerini buradan takip edebilirsiniz.</p>
        </div>
        <SubscriptionStatusBadge status={subscription.status} />
      </header>

      <section className="subscription-management__identity" aria-label="Abonelik özeti">
        <div>
          <span>Paket</span>
          <strong>{subscription.packageName}</strong>
        </div>
        <div>
          <span>Koçunuz</span>
          <strong>{subscription.coachName}</strong>
        </div>
      </section>

      <dl className="subscription-management__facts">
        <div>
          <dt>Başlangıç tarihi</dt>
          <dd>{formatDate(subscription.startAt)}</dd>
        </div>
        <div>
          <dt>Dönem sonu</dt>
          <dd>{formatDate(subscription.endAt)}</dd>
        </div>
        <div>
          <dt>Otomatik yenileme</dt>
          <dd>{subscription.autoRenew ? "Açık" : "Kapalı"}</dd>
        </div>
        <div>
          <dt>Son ödeme</dt>
          <dd className="subscription-management__payment">
            {payment ? (
              <>
                <span>{formatAmount(payment.amount)}</span>
                <PaymentStatusBadge status={payment.status} />
              </>
            ) : "Henüz ödeme kaydı yok"}
          </dd>
        </div>
      </dl>

      {!subscription.autoRenew && (
        <p className="subscription-management__renewal-note" role="status">
          Otomatik yenileme kapalı. Mevcut erişiminiz <strong>{formatDate(subscription.endAt)}</strong> tarihine kadar devam eder.
        </p>
      )}

      <div className="subscription-management__actions" aria-label="Abonelik işlemleri">
        <Link className="subscription-management__action" to={`/coaches/${subscription.coachId}`}>
          Koç Profilini Gör
        </Link>
        <Link className="subscription-management__action" to="/bookings">Görüşmelerim</Link>
        <Link className="subscription-management__action" to="/messages">Mesajlarım</Link>
      </div>

      {subscription.autoRenew && (
        <div className="subscription-management__cancellation">
          <div>
            <strong>Otomatik yenileme</strong>
            <p>Yenilemeyi iptal ettiğinizde mevcut dönem sonuna kadar erişiminiz devam eder.</p>
          </div>
          <button type="button" onClick={() => setShowCancelModal(true)}>Yenilemeyi İptal Et</button>
        </div>
      )}

      <RefundRequestAction subscriptionId={subscription.id} onSuccess={onRefresh} />

      {showCancelModal && (
        <CancelRenewalModal
          subscriptionId={subscription.id}
          formattedEndAt={formatDate(subscription.endAt)}
          onClose={() => setShowCancelModal(false)}
          onSuccess={handleCancelSuccess}
        />
      )}
    </article>
  );
};
