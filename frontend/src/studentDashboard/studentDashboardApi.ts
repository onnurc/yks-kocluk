import { httpClient } from "../api/httpClient";
import type { StudentDashboardResponse } from "./studentDashboardTypes";

export const studentDashboardApi = {
  getDashboardData: async (): Promise<StudentDashboardResponse> => {
    return httpClient.get<StudentDashboardResponse>("/api/v1/students/me/dashboard");
  },
};
