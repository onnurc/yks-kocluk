export type SessionStatus = "PLANNED" | "COMPLETED" | "CANCELLED" | "LATE_CANCELLED" | "NO_SHOW";

export interface SessionCreateRequest {
  availabilityId: number;
}

export interface SessionResponse {
  id: number;
  coachProfileId: number;
  coachName: string;
  studentName: string;
  availabilityId: number;
  status: SessionStatus;
  startTime: string;
  endTime: string;
  meetLink: string | null;
}

export interface AvailabilityResponse {
  id: number;
  coachProfileId: number;
  startTime: string;
  endTime: string;
  booked: boolean;
}
