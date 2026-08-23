import { httpClient } from "../api/httpClient";
import type { AvailabilityResponse, CoachDashboardSummaryResponse, PageResponse, SessionResponse } from "./coachDashboardTypes";

export const coachDashboardApi = {
  getSummary: () => httpClient.get<CoachDashboardSummaryResponse>("/api/v1/coach/dashboard/summary"),
  getUpcomingSessions: () => {
    const from = encodeURIComponent(new Date().toISOString());
    return httpClient.get<PageResponse<SessionResponse>>(`/api/v1/coach/calendar/sessions?from=${from}&status=PLANNED&page=0&size=4&sort=startTime,asc`);
  },
  getAvailability: () => httpClient.get<AvailabilityResponse[]>("/api/v1/coach/availability"),
};
