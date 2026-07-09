import React from "react";
import type { SubscriptionStatus } from "./studentDashboardTypes";

export const SubscriptionStatusBadge: React.FC<{ status: SubscriptionStatus }> = ({ status }) => {
  let backgroundColor = "#6c757d";
  let color = "white";
  let text: string = status;

  switch (status) {
    case "ACTIVE":
      backgroundColor = "#28a745";
      text = "Aktif";
      break;
    case "PENDING_PAYMENT":
      backgroundColor = "#ffc107";
      color = "#212529";
      text = "Ödeme Bekleniyor";
      break;
    case "CANCELLED":
      backgroundColor = "#dc3545";
      text = "Yenileme İptal";
      break;
    case "PAST_DUE":
      backgroundColor = "#fd7e14";
      text = "Ödeme Gecikmede";
      break;
    case "TERMINATED":
      backgroundColor = "#343a40";
      text = "Sonlandırıldı";
      break;
    case "EXPIRED":
      backgroundColor = "#6c757d";
      text = "Süresi Doldu";
      break;
  }

  return (
    <span style={{ padding: "0.25rem 0.5rem", borderRadius: "4px", backgroundColor, color, fontWeight: "bold", fontSize: "0.85rem" }}>
      {text}
    </span>
  );
};
