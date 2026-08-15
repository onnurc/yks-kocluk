export type Track = "NUMERICAL" | "EQUAL_WEIGHT" | "VERBAL" | "LANGUAGE";

export const TRACK_LABELS: Record<Track, string> = {
  NUMERICAL: "Sayısal",
  EQUAL_WEIGHT: "Eşit Ağırlık",
  VERBAL: "Sözel",
  LANGUAGE: "Dil",
};

export interface CoachSummaryResponse {
  id: number;
  fullName: string;
  headline: string;
  universityName: string;
  tracks: string[];
  rating: number | null;
  totalSessions: number;
  acceptingNewStudents: boolean;
  profileImageUrl: string | null;
  introVideoUrl: string | null;
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
  profileImageUrl: string | null;
  introVideoUrl: string | null;
}

export type PublicCoachDetailResponse = Omit<CoachDetailResponse, "userId">;

export interface PackageResponse {
  id: number;
  name: string;
  weeklySessions: number;
  durationDays: number;
  price: number;
}
