import { httpClient } from "../api/httpClient";
import type {
  ConversationResponse,
  MessageResponse,
  PageResponse,
  AdminConversationSummary,
} from "./messagingTypes";

export const messagingApi = {
  openConversation: async (coachId: number): Promise<ConversationResponse> => {
    return httpClient.post<ConversationResponse>("/api/v1/conversations", { coachId });
  },

  listConversations: async (): Promise<ConversationResponse[]> => {
    return httpClient.get<ConversationResponse[]>("/api/v1/conversations");
  },

  listMessages: async (
    conversationId: number,
    page = 0,
    size = 30
  ): Promise<PageResponse<MessageResponse>> => {
    return httpClient.get<PageResponse<MessageResponse>>(
      `/api/v1/conversations/${conversationId}/messages?page=${page}&size=${size}`
    );
  },

  sendMessage: async (
    conversationId: number,
    content: string
  ): Promise<MessageResponse> => {
    return httpClient.post<MessageResponse>(
      `/api/v1/conversations/${conversationId}/messages`,
      { content }
    );
  },

  markRead: async (conversationId: number): Promise<void> => {
    return httpClient.post<void>(`/api/v1/conversations/${conversationId}/read`);
  },

  listAdminConversations: async (page = 0, size = 20): Promise<PageResponse<AdminConversationSummary>> => {
    return httpClient.get<PageResponse<AdminConversationSummary>>(
      `/api/v1/admin/conversations?page=${page}&size=${size}&sort=lastMessageAt,desc`
    );
  },

  listAdminMessages: async (
    conversationId: number,
    reason: string,
    page = 0,
    size = 20
  ): Promise<PageResponse<MessageResponse>> => {
    return httpClient.get<PageResponse<MessageResponse>>(
      `/api/v1/admin/conversations/${conversationId}/messages?reason=${encodeURIComponent(reason)}&page=${page}&size=${size}&sort=createdAt,desc`
    );
  },
};
