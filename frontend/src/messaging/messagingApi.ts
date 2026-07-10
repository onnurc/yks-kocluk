import { httpClient } from "../api/httpClient";
import type {
  ConversationResponse,
  MessageResponse,
  PageResponse,
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
};
