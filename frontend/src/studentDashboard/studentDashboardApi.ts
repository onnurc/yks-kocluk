import { httpClient } from "../api/httpClient";
import type { StudentDashboardResponse } from "./studentDashboardTypes";

export const studentDashboardApi = {
  getDashboardData: async (): Promise<StudentDashboardResponse> => {
    return httpClient.get<StudentDashboardResponse>("/api/v1/students/me/dashboard");
  },
};

export const SUBSCRIPTION_STATE_CHANGED_EVENT = "uniform:subscription-state-changed";

export const notifySubscriptionStateChanged = (): void => {
  window.dispatchEvent(new Event(SUBSCRIPTION_STATE_CHANGED_EVENT));
};
