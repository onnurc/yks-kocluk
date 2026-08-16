export interface ConversationCreateRequest {
  coachId: number;
}

export interface ConversationResponse {
  id: number;
  studentName: string;
  coachProfileId: number;
  coachName: string;
  lastMessageAt: string;
  unreadCount: number;
  observer: {
    type: "ADMIN";
    displayName: string;
    readOnly: true;
  };
}

export interface MessageSendRequest {
  content: string;
}

export interface MessageResponse {
  id: number;
  conversationId: number;
  senderId: number;
  senderName: string;
  content: string;
  sentAt: string;
  readAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface AdminConversationSummary {
  conversationId: number;
  student: { id: number; fullName: string };
  coach: { id: number; fullName: string; universityName: string };
  lastMessageAt: string;
  messageCount: number;
  observer: { type: "ADMIN"; displayName: string; readOnly: true };
}
