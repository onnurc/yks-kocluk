import { httpClient } from "../api/httpClient";

export interface RefundEligibilityResponse {
  subscriptionId:number; eligible:boolean;
  status:"ELIGIBLE"|"NON_REFUNDABLE"|"WINDOW_EXPIRED"|"NO_REFUNDABLE_BALANCE"|"ACTIVE_REQUEST_EXISTS"|"PAYMENT_NOT_ELIGIBLE";
  refundableAmount:number; currency:string;
  deadline:string|null; explanation:string; activeRequestStatus:string|null;
  packageType:"ONE_MONTH"|"THREE_MONTHS"|"UNTIL_EXAM"|null;
  policy:string; cancellationRequestedAt:string; usedMonthCount:number;
  currentServicePeriodEnd:string; accessEndsAt:string; consumedAmount:number;
}

export interface RefundRequestResponse { id:number; status:string; refundableAmount?:number; }

export const refundRequestApi = {
  eligibility: (subscriptionId:number) => httpClient.get<RefundEligibilityResponse>(`/api/v1/refund-requests/eligibility?subscriptionId=${subscriptionId}`),
  create: (subscriptionId:number) => httpClient.post<RefundRequestResponse>("/api/v1/refund-requests", { subscriptionId }),
};
