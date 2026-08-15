export type TrialConsultationStatus = "REQUESTED" | "CONFIRMED" | "COMPLETED" | "NO_SHOW" | "CANCELLED";

export interface TrialConsultationCreateRequest {
  availabilityId: number;
}

export interface TrialConsultationResponse {
  id: number;
  coachProfileId: number;
  coachName: string;
  studentId: number;
  studentName: string;
  availabilityId: number;
  startsAt: string;
  endsAt: string;
  status: TrialConsultationStatus;
  requestedAt: string;
  updatedAt: string;
}
