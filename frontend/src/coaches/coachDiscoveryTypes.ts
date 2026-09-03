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
  introVideoEmbedUrl: string | null;
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
  introVideoEmbedUrl: string | null;
}

export type PublicCoachDetailResponse = Omit<CoachDetailResponse, "userId">;

export interface PackageOfferResponse {
  id: number;
  packageType: "ONE_MONTH" | "THREE_MONTHS" | "UNTIL_EXAM";
  name: string;
  active: boolean;
  purchasable: boolean;
  durationMonths: number | null;
  untilExamMonthsRemaining: number | null;
  listPrice: number | null;
  effectivePrice: number | null;
  campaignActive: boolean;
  campaignTitle: string | null;
  campaignDescription: string | null;
  evaluationMeetingsPerMonth: number;
  weeklyMeetingsPerMonth: number;
  totalMeetingsPerMonth: number;
}
