import { httpClient } from "../api/httpClient";

export const subscriptionManagementApi = {
  cancelRenewal: (subscriptionId: number): Promise<unknown> => {
    return httpClient.post<unknown>(`/api/v1/subscriptions/${subscriptionId}/cancel-renewal`);
  },
};
