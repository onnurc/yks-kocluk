import React from "react";
import type { PaymentStatus } from "./studentDashboardTypes";

export const PaymentStatusBadge: React.FC<{ status: PaymentStatus }> = ({ status }) => {
  let text: string = status;

  switch (status) {
    case "SUCCESS":
      text = "Başarılı";
      break;
    case "PENDING":
      text = "Beklemede";
      break;
    case "FAILED":
      text = "Başarısız";
      break;
    case "REFUNDED":
      text = "İade Edildi";
      break;
  }

  return (
    <span className={`subscription-management__badge subscription-management__badge--${status.toLowerCase()}`}>
      {text}
    </span>
  );
};
