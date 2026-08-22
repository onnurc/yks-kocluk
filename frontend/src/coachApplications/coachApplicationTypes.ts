export type CoachApplicationStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface CoachApplicationResponse {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  experience: string | null;
  status: CoachApplicationStatus;
  createdAt: string;
  reviewedAt: string | null;
  reviewNote: string | null;
  linkedUserId: number | null;
}
