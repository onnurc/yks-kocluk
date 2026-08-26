export type CoachTrack = "NUMERICAL" | "VERBAL" | "EQUAL_WEIGHT" | "LANGUAGE";

export interface StudentProfileResponse {
  id: number;
  userId: number;
  fullName: string;
  email: string;
  gradeLevel: string | null;
  city: string | null;
  examYear: number | null;
  yksScoreType: CoachTrack | null;
  examSession: "TYT" | "AYT" | "YDT" | null;
  targetUniversity: string | null;
  targetDepartment: string | null;
  profileImageUrl: string | null;
  profileImageAssetId: number | null;
}

export interface StudentProfileUpdateRequest {
  gradeLevel: string | null;
  city: string | null;
  examYear: number | null;
  yksScoreType: CoachTrack | null;
  examSession: "TYT" | "AYT" | "YDT" | null;
  targetUniversity: string | null;
  targetDepartment: string | null;
}

export interface CoachProfileResponse {
  id: number;
  userId: number;
  fullName: string;
  email: string;
  headline: string | null;
  bio: string | null;
  universityId: number | null;
  universityName: string | null;
  department: string | null;
  graduationYear: number | null;
  yksRanking: number | null;
  status: "PENDING" | "APPROVED" | "REJECTED";
  rejectionReason: string | null;
  tracks: CoachTrack[];
  activeStudentCount: number;
  maxStudentCapacity: number;
  payoutAccountReady: boolean;
  profileImageUrl: string | null;
  profileImageAssetId: number | null;
  introVideoEmbedUrl: string | null;
}

export interface CoachProfileUpdateRequest {
  headline: string | null;
  bio: string | null;
  universityId: number;
  department: string | null;
  graduationYear: number | null;
  yksRanking: number | null;
  tracks: CoachTrack[];
}

export interface CoachEducationUpdateRequest {
  university: string;
  department: string | null;
  yksRanking: number | null;
}

export interface MediaPresignResponse {
  assetId: number;
  uploadUrl: string;
  requiredHeaders: Record<string, string>;
}

export interface MediaAssetResponse {
  id: number;
  url: string | null;
}

