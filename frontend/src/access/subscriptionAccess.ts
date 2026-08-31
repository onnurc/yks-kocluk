import type { SubscriptionStatus } from "../studentDashboard/studentDashboardTypes";

export const canBookWithSubscription = (status: SubscriptionStatus | undefined | null): boolean => {
  return isLiveCoachRelationship(status);
};

export const canMessageWithSubscription = (status: SubscriptionStatus | undefined | null): boolean => {
  return isLiveCoachRelationship(status);
};

export const isLiveCoachRelationship = (status: SubscriptionStatus | undefined | null): boolean =>
  status === "ACTIVE" || status === "PAST_DUE";

export const blocksNewCoachCheckout = (status: SubscriptionStatus | undefined | null): boolean =>
  status === "PENDING_PAYMENT" || isLiveCoachRelationship(status);
