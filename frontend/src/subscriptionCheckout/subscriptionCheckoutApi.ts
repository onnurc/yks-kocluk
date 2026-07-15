import { httpClient } from "../api/httpClient";
import type {
  SubscriptionCheckoutRequest,
  SubscriptionCheckoutResponse,
  SubscriptionResponse,
} from "./subscriptionCheckoutTypes";

export const subscriptionCheckoutApi = {
  checkout: async (
    request: SubscriptionCheckoutRequest
  ): Promise<SubscriptionCheckoutResponse> => {
    return httpClient.post<SubscriptionCheckoutResponse>(
      "/api/v1/subscriptions/checkout",
      request
    );
  },

  stubSucceed: async (paymentId: number): Promise<SubscriptionResponse> => {
    return httpClient.post<SubscriptionResponse>(`/api/v1/payments/${paymentId}/stub/succeed`);
  },
};
