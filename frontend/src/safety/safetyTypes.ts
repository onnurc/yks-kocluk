export interface ConsentResponse {
  consentId: number;
  consentType: string;
  documentVersion: string;
  acceptedAt: string;
}

export interface ConsentStatusResponse {
  currentVersion: string;
  hasConsented: boolean;
}

export type ReportTargetType = "USER" | "CONVERSATION" | "MESSAGE";
export type ReportStatus = "OPEN" | "REVIEWED" | "RESOLVED" | "DISMISSED";

export interface ReportResponse {
  id: number;
  reporterUserId: number;
  targetType: ReportTargetType;
  targetId: number;
  reason: string;
  details: string | null;
  status: ReportStatus;
  createdAt: string;
  reviewedAt: string | null;
  reviewedByAdminId: number | null;
}

export interface SuspendResponse {
  userId: number;
  status: string;
  reason: string | null;
}

export interface AdminConversationAccessResponse {
  id: number;
  adminUserId: number;
  conversationId: number;
  reason: string;
  accessedAt: string;
}
