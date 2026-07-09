import React from "react";
import type { PaymentStatus } from "./studentDashboardTypes";

export const PaymentStatusBadge: React.FC<{ status: PaymentStatus }> = ({ status }) => {
  let backgroundColor = "#6c757d";
  let color = "white";
  let text: string = status;

  switch (status) {
    case "SUCCESS":
      backgroundColor = "#28a745";
      text = "Başarılı";
      break;
    case "PENDING":
      backgroundColor = "#ffc107";
      color = "#212529";
      text = "Beklemede";
      break;
    case "FAILED":
      backgroundColor = "#dc3545";
      text = "Başarısız";
      break;
    case "REFUNDED":
      backgroundColor = "#17a2b8";
      text = "İade Edildi";
      break;
  }

  return (
    <span style={{ padding: "0.25rem 0.5rem", borderRadius: "4px", backgroundColor, color, fontWeight: "bold", fontSize: "0.85rem" }}>
      {text}
    </span>
  );
};
