export interface ConversationCreateRequest {
  coachId: number;
}

export interface ConversationResponse {
  id: number;
  studentName: string;
  coachProfileId: number;
  coachName: string;
  lastMessage: string | null;
  lastMessageAt: string | null;
  unreadCount: number;
  counterpartUserId: number;
  counterpartOnline: boolean;
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

export interface PresenceResponse {
  userId: number;
  online: boolean;
}

/**
 * Pushed on /user/queue/notifications. `unreadTotal` is computed server-side on every push, so
 * it is assigned to the badge rather than added to it. Carries no message text by design — see
 * MailClient.sendNewMessageNotification's javadoc for the same reasoning applied to email.
 */
export interface UserNotificationResponse {
  type: "NEW_MESSAGE";
  conversationId: number;
  unreadTotal: number;
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
