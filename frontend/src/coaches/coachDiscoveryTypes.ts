export interface CoachSummaryResponse {
  id: number;
  fullName: string;
  headline: string;
  universityName: string;
  tracks: string[];
  rating: number | null;
  totalSessions: number;
  acceptingNewStudents: boolean;
}

export interface CoachDetailResponse {
  id: number;
  userId: number;
  fullName: string;
  headline: string;
  bio: string;
  universityName: string;
  department: string;
  graduationYear: number;
  tracks: string[];
  rating: number | null;
  totalSessions: number;
  acceptingNewStudents: boolean;
}

export interface PackageResponse {
  id: number;
  name: string;
  weeklySessions: number;
  durationDays: number;
  price: number;
}
