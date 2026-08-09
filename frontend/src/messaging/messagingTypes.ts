export interface ConversationCreateRequest {
  coachId: number;
}

export interface ConversationResponse {
  id: number;
  studentId: number;
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
  createdAt: string;
  readAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  pageNumber: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
