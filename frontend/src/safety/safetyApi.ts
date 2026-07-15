import { httpClient } from "../api/httpClient";
import type { PageResponse, MessageResponse } from "../messaging/messagingTypes";
import type {
  ConsentResponse,
  ConsentStatusResponse,
  ReportResponse,
  SuspendResponse,
  AdminConversationAccessResponse,
} from "./safetyTypes";

export const safetyApi = {
  checkConsentStatus: (consentType: string): Promise<ConsentStatusResponse> => {
    return httpClient.get<ConsentStatusResponse>(
      `/api/v1/consents/status?consentType=${consentType}`
    );
  },

  recordConsent: (consentType: string, documentVersion: string): Promise<ConsentResponse> => {
    return httpClient.post<ConsentResponse>("/api/v1/consents", {
      consentType,
      documentVersion,
    });
  },

  revokeConsent: (consentType: string): Promise<ConsentResponse> => {
    return httpClient.post<ConsentResponse>(
      `/api/v1/consents/revoke?consentType=${consentType}`
    );
  },

  createReport: (
    targetType: string,
    targetId: number,
    reason: string,
    details?: string
  ): Promise<ReportResponse> => {
    return httpClient.post<ReportResponse>("/api/v1/reports", {
      targetType,
      targetId,
      reason,
      details: details || null,
    });
  },

  listReports: (status?: string, page: number = 0, size: number = 20): Promise<PageResponse<ReportResponse>> => {
    const statusQuery = status ? `&status=${status}` : "";
    return httpClient.get<PageResponse<ReportResponse>>(
      `/api/v1/admin/reports?page=${page}&size=${size}${statusQuery}`
    );
  },

  suspendUser: (userId: number, reason: string): Promise<SuspendResponse> => {
    return httpClient.post<SuspendResponse>(`/api/v1/admin/users/${userId}/suspend`, {
      reason,
    });
  },

  logAccess: (conversationId: number, reason: string): Promise<AdminConversationAccessResponse> => {
    return httpClient.post<AdminConversationAccessResponse>(
      `/api/v1/admin/conversations/${conversationId}/access-log`,
      { reason }
    );
  },

  getConversationMessages: (
    conversationId: number,
    reason: string,
    page: number = 0,
    size: number = 20
  ): Promise<PageResponse<MessageResponse>> => {
    return httpClient.post<PageResponse<MessageResponse>>(
      `/api/v1/admin/conversations/${conversationId}/messages?page=${page}&size=${size}`,
      { reason }
    );
  },
};
