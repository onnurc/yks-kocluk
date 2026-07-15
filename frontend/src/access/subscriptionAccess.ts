import type { SubscriptionStatus } from "../studentDashboard/studentDashboardTypes";

export const canBookWithSubscription = (status: SubscriptionStatus | undefined | null): boolean => {
  if (!status) return false;
  return status === "ACTIVE" || status === "PAST_DUE";
};

export const canMessageWithSubscription = (status: SubscriptionStatus | undefined | null): boolean => {
  if (!status) return false;
  return status === "ACTIVE" || status === "PAST_DUE";
};
