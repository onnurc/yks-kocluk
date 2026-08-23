import type { AvailabilityResponse, SessionResponse } from "../booking/bookingTypes";

export interface CoachDashboardEventResponse {
  id: number;
  type: "PAID_SESSION" | "TRIAL_CONSULTATION";
  startsAt: string;
  endsAt: string;
  studentId: number;
  studentName: string;
}

export interface CoachDashboardSummaryResponse {
  activeStudentCount: number;
  completedSessionsThisMonth: number;
  upcomingSessionCount: number;
  unreadMessageCount: number;
  availabilityConfigured: boolean;
  nextSession: CoachDashboardEventResponse | null;
  pendingTrialConsultationCount: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export type { AvailabilityResponse, SessionResponse };
