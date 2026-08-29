export interface AdminPaymentResponse {
  id: number;
  subscriptionId: number;
  studentEmail: string;
  studentFullName: string;
  coachFullName: string;
  packageName: string;
  type: string;
  amount: number;
  status: string;
  providerReference: string;
  createdAt: string;
  succeededAt: string | null;
  refundedAmount: number;
  remainingRefundableAmount: number;
  refundEligible: boolean;
  refundDeadline: string | null;
  refundIneligibleReason: string | null;
}

export interface AdminFinanceSummary {
  from: string | null;
  to: string | null;
  grossRevenue: number;
  successfulPaymentCount: number;
  failedPaymentCount: number;
  pendingPaymentCount: number;
  refundTotal: number;
  netCollectedAmount: number;
}

export interface AdminSubscriptionResponse {
  id: number;
  studentEmail: string;
  studentFullName: string;
  coachFullName: string;
  packageName: string;
  status: string;
  startAt: string;
  endAt: string;
  autoRenew: boolean;
  cancelledAt: string | null;
  terminationReason: string | null;
  createdAt: string;
}

export interface RefundResponse {
  originalPaymentId: number;
  refundPaymentId: number;
  refundStatus: string;
  amount: number;
  remainingRefundableAmount: number;
  message: string;
}

export interface AdminSubscriptionTerminateResponse {
  subscriptionId: number;
  status: string;
  terminatedAt: string;
  message: string;
}

export interface AdminRefundAuditResponse {
  id: number;
  status: "PENDING" | "APPROVED" | "REJECTED" | "REFUNDED";
  requestedAt: string;
  studentName: string;
  coachName: string;
  packageName: string;
  subscriptionId: number;
  originalPaymentId: number;
  refundPaymentId: number | null;
  amount: number;
  refundedAmount: number;
  currency: string;
}
