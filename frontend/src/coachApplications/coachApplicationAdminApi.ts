import { httpClient } from "../api/httpClient";
import type { PageResponse } from "../messaging/messagingTypes";
import type { CoachApplicationResponse, CoachApplicationStatus } from "./coachApplicationTypes";

export const coachApplicationAdminApi = {
  list: (status: CoachApplicationStatus, page: number, size = 20): Promise<PageResponse<CoachApplicationResponse>> =>
    httpClient.get<PageResponse<CoachApplicationResponse>>(
      `/api/v1/admin/coach-applications?status=${status}&page=${page}&size=${size}`
    ),

  approve: (id: number): Promise<CoachApplicationResponse> =>
    httpClient.post<CoachApplicationResponse>(`/api/v1/admin/coach-applications/${id}/approve`),

  reject: (id: number, reason: string): Promise<CoachApplicationResponse> =>
    httpClient.post<CoachApplicationResponse>(`/api/v1/admin/coach-applications/${id}/reject`, { reason }),
};
