import { httpClient } from "../api/httpClient";
import type { PageResponse } from "../messaging/messagingTypes";
import type {
  AdminPaymentResponse,
  AdminSubscriptionResponse,
  RefundResponse,
  AdminSubscriptionTerminateResponse,
  AdminFinanceSummary,
  AdminRefundAuditResponse,
} from "./financeTypes";

export const financeApi = {
  summary: (): Promise<AdminFinanceSummary> => httpClient.get<AdminFinanceSummary>("/api/v1/admin/finance/summary"),
  listPayments: (page: number = 0, size: number = 20): Promise<PageResponse<AdminPaymentResponse>> => {
    return httpClient.get<PageResponse<AdminPaymentResponse>>(
      `/api/v1/admin/payments?page=${page}&size=${size}`
    );
  },

  listSubscriptions: (page: number = 0, size: number = 20): Promise<PageResponse<AdminSubscriptionResponse>> => {
    return httpClient.get<PageResponse<AdminSubscriptionResponse>>(
      `/api/v1/admin/subscriptions?page=${page}&size=${size}`
    );
  },

  listRefundAudit: (page: number = 0, size: number = 20): Promise<PageResponse<AdminRefundAuditResponse>> =>
    httpClient.get<PageResponse<AdminRefundAuditResponse>>(
      `/api/v1/admin/refund-requests?page=${page}&size=${size}&sort=requestedAt,desc`
    ),

  refund: (paymentId: number, amount: number, reason: string): Promise<RefundResponse> => {
    return httpClient.post<RefundResponse>(`/api/v1/admin/payments/${paymentId}/refund`, {
      amount,
      reason,
    });
  },

  terminateSubscription: (subscriptionId: number, reason: string): Promise<AdminSubscriptionTerminateResponse> => {
    return httpClient.post<AdminSubscriptionTerminateResponse>(
      `/api/v1/admin/subscriptions/${subscriptionId}/terminate`,
      { reason }
    );
  },
};
