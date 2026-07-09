import type { CurrentUser } from "../auth/authTypes";

export type SubscriptionStatus =
  | "PENDING_PAYMENT"
  | "ACTIVE"
  | "CANCELLED"
  | "PAST_DUE"
  | "TERMINATED"
  | "EXPIRED";

export type PaymentStatus =
  | "PENDING"
  | "SUCCEEDED"
  | "FAILED"
  | "REFUNDED";

export interface DashboardSubscription {
  id: number;
  status: SubscriptionStatus;
  coachId: number;
  coachName: string;
  packageId: number;
  packageName: string;
  startAt: string;
  endAt: string;
  autoRenew: boolean;
  cancelledAt: string | null;
  terminationReason: string | null;
}

export interface DashboardPayment {
  id: number;
  status: PaymentStatus;
  amount: number;
  createdAt: string;
}

export interface StudentDashboardResponse {
  user: CurrentUser;
  subscription: DashboardSubscription | null;
  payment: DashboardPayment | null;
}
