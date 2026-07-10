import type { SubscriptionStatus, PaymentStatus } from "../studentDashboard/studentDashboardTypes";

export interface SubscriptionCheckoutRequest {
  coachId: number;
  packageId: number;
}

export interface SubscriptionCheckoutResponse {
  subscriptionId: number;
  paymentId: number;
  subscriptionStatus: SubscriptionStatus;
  paymentStatus: PaymentStatus;
  amount: number;
  checkoutToken: string;
  checkoutUrl: string;
}

export interface SubscriptionResponse {
  id: number;
  coachProfileId: number;
  coachName: string;
  packageId: number;
  packageName: string;
  weeklySessions: number;
  status: SubscriptionStatus;
  startAt: string;
  endAt: string;
}
