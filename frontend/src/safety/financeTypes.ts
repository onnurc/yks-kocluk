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
  refundedAmount: number;
  remainingRefundableAmount: number;
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
