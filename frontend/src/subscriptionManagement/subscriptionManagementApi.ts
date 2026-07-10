import { httpClient } from "../api/httpClient";

export const subscriptionManagementApi = {
  cancelRenewal: (subscriptionId: number): Promise<any> => {
    return httpClient.post<any>(`/api/v1/subscriptions/${subscriptionId}/cancel-renewal`);
  },
};
