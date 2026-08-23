import React from "react";
import type { SubscriptionStatus } from "./studentDashboardTypes";

export const SubscriptionStatusBadge: React.FC<{ status: SubscriptionStatus }> = ({ status }) => {
  let text: string = status;

  switch (status) {
    case "ACTIVE":
      text = "Aktif";
      break;
    case "PENDING_PAYMENT":
      text = "Ödeme Bekleniyor";
      break;
    case "CANCELLED":
      text = "Yenileme İptal";
      break;
    case "PAST_DUE":
      text = "Ödeme Gecikmede";
      break;
    case "TERMINATED":
      text = "Sonlandırıldı";
      break;
    case "EXPIRED":
      text = "Süresi Doldu";
      break;
  }

  return (
    <span className={`subscription-management__badge subscription-management__badge--${status.toLowerCase()}`}>
      {text}
    </span>
  );
};
