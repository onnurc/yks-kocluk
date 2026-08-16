import React, { useCallback, useEffect, useState, useRef } from "react";
import { useParams, Link } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { messagingApi } from "../messaging/messagingApi";
import type { MessageResponse } from "../messaging/messagingTypes";
import { useConversationSocket } from "../messaging/useConversationSocket";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { canMessageWithSubscription } from "../access/subscriptionAccess";
import { FormError } from "../components/FormError";
import { ReportModal } from "../safety/ReportModal";

const PENDING_SEND_TIMEOUT_MS = 6000;

export const ConversationPage: React.FC = () => {
  const { conversationId } = useParams<{ conversationId: string }>();
  const id = Number(conversationId);
  const { user } = useAuth();
  const isStudent = user?.role === "STUDENT";

  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [dashboardData, setDashboardData] = useState<StudentDashboardResponse | null>(null);
  const [inputText, setInputText] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(true);
  const [sendLoading, setSendLoading] = useState<boolean>(false);
  const [pendingContent, setPendingContent] = useState<string | null>(null);
  const [error, setError] = useState<any | null>(null);
  const [reportTarget, setReportTarget] = useState<{ type: "CONVERSATION" | "MESSAGE"; id: number } | null>(null);

  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const pendingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Single chokepoint for adding messages to state, whatever the source (live broadcast,
  // history load, reconnect re-sync) — id-based dedupe means the same message id is never
  // rendered twice regardless of which path(s) it arrived through.
  const mergeMessages = useCallback((incoming: MessageResponse[]) => {
    setMessages((prev) => {
      const existingIds = new Set(prev.map((m) => m.id));
      const newOnes = incoming.filter((m) => !existingIds.has(m.id));
      if (newOnes.length === 0) return prev;
      return [...prev, ...newOnes].sort(
        (a, b) => new Date(a.sentAt).getTime() - new Date(b.sentAt).getTime()
      );
    });
  }, []);

  const clearPendingSend = () => {
    if (pendingTimeoutRef.current) {
      clearTimeout(pendingTimeoutRef.current);
      pendingTimeoutRef.current = null;
    }
    setPendingContent(null);
    setSendLoading(false);
  };

  const loadMessagesAndDetails = async () => {
    setError(null);
    try {
      // Coach senders aren't subscription-gated (see MessagesPage.tsx for the same reasoning),
      // so skip the student-only dashboard call when the viewer is the coach.
      const [msgPage, dash] = await Promise.all([
        messagingApi.listMessages(id, 0, 100),
        isStudent ? studentDashboardApi.getDashboardData() : Promise.resolve(null),
      ]);
      // The API returns messages in descending order (newest first).
      // We reverse them to display in standard chronological order (oldest first).
      const sorted = [...(msgPage.content || [])].reverse();
      setMessages(sorted);
      setDashboardData(dash);
      void markConversationRead();
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  // Fires once the WS reconnects after an actual drop (not the initial connect) — the
  // in-memory broker doesn't buffer anything missed while disconnected, so re-sync the
  // latest page. Merged in via mergeMessages, not a wholesale replace: a full replace would
  // discard anything the user has scrolled further back to load, and would jump the scroll
  // position even for what's already on screen.
  const handleReconnected = useCallback(() => {
    messagingApi
      .listMessages(id, 0, 100)
      .then((page) => mergeMessages([...(page.content || [])].reverse()))
      .catch(() => {
        // Best-effort re-sync; the live subscription (already re-established) still carries
        // anything sent from this point forward.
      });
  }, [id, mergeMessages]);

  const markConversationRead = async () => {
    try {
      await messagingApi.markRead(id);
      window.dispatchEvent(new Event("messages-read"));
    } catch {
      // Non-critical — the unread badge just stays stale until the next successful call.
    }
  };

  const handleSocketMessage = useCallback(
    (message: MessageResponse) => {
      mergeMessages([message]);
      if (message.senderId === user?.id) {
        // Resolves our own pending "gönderiliyor" indicator — only one send is in flight at
        // a time (input/button are disabled while sendLoading is true), so any self-authored
        // broadcast arriving while pending is that send's resolution.
        clearPendingSend();
      } else {
        // Actively viewing this conversation when the other party's message arrives —
        // treat it as read immediately rather than leaving the badge stuck until next open.
        void markConversationRead();
      }
    },
    [mergeMessages, user?.id]
  );

  const { status: socketStatus, sendViaSocket } = useConversationSocket({
    conversationId: id || null,
    onMessage: handleSocketMessage,
    onReconnected: handleReconnected,
  });

  useEffect(() => {
    if (id) {
      loadMessagesAndDetails();
    }
  }, [id]);

  useEffect(() => {
    return () => {
      if (pendingTimeoutRef.current) {
        clearTimeout(pendingTimeoutRef.current);
      }
    };
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, pendingContent]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanText = inputText.trim();
    if (!cleanText || sendLoading) return;

    if (isStudent && !canMessageWithSubscription(dashboardData?.subscription?.status)) {
      setError(new Error("Mesaj göndermek için geçerli bir aboneliğiniz olmalıdır."));
      return;
    }

    setError(null);

    // Prefer the live socket — it's the only send path the backend broadcasts from, so it's
    // what makes the message show up on the other side without a refresh (REST alone never
    // pushes to /topic/conversations/{id}; only ChatStompController does). No optimistic
    // append here: we're also subscribed to our own broadcast, so the message lands in state
    // via the same path as the other party's — one source of truth, no duplicate handling
    // needed. If the broadcast doesn't come back in time, clear the pending state and let the
    // user retry (kept in the input) rather than silently re-sending over REST, which could
    // create a second, genuinely duplicate message if the socket send actually did succeed.
    if (sendViaSocket(cleanText)) {
      setSendLoading(true);
      setPendingContent(cleanText);
      setInputText("");
      pendingTimeoutRef.current = setTimeout(() => {
        clearPendingSend();
        setInputText(cleanText);
        setError(new Error("Mesaj gönderilemedi. Bağlantınızı kontrol edip tekrar deneyin."));
      }, PENDING_SEND_TIMEOUT_MS);
      return;
    }

    // WebSocket not connected — fall back to the original REST path so chat keeps working.
    setSendLoading(true);
    try {
      const response = await messagingApi.sendMessage(id, cleanText);
      mergeMessages([response]);
      setInputText(""); // Clear only after success
    } catch (err) {
      setError(err); // Typed message is NOT lost, remains in inputText state
    } finally {
      setSendLoading(false);
    }
  };

  const formatTime = (isoString: string) => {
    const d = new Date(isoString);
    return d.toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" });
  };

  const subStatus = dashboardData?.subscription?.status;
  const isMessageAllowed = isStudent ? canMessageWithSubscription(subStatus) : true;
  const isPendingPayment = isStudent && subStatus === "PENDING_PAYMENT";

  // Determine the other party's name for the page title
  const coachName = messages.length > 0
    ? (messages[0].senderId === user?.id ? "" : messages[0].senderName)
    : (isStudent ? "Koçunuz" : "Öğrenciniz");

  const socketStatusMeta: Record<typeof socketStatus, { label: string; color: string }> = {
    connected: { label: "Bağlı", color: "#28a745" },
    connecting: { label: "Bağlanıyor…", color: "#6c757d" },
    reconnecting: { label: "Yeniden bağlanıyor…", color: "#ffc107" },
    disconnected: { label: "Bağlantı kesik", color: "#dc3545" },
  };

  return (
    <div style={{ padding: "2rem", maxWidth: "800px", margin: "0 auto", display: "flex", flexDirection: "column", height: "calc(100vh - 120px)" }}>
      <div style={{ marginBottom: "0.4rem", display: "flex", justifyContent: "flex-end", alignItems: "center", gap: "0.4rem" }}>
        <span style={{ display: "inline-block", width: "8px", height: "8px", borderRadius: "50%", backgroundColor: socketStatusMeta[socketStatus].color }} />
        <span style={{ fontSize: "0.75rem", color: "#6c757d" }}>{socketStatusMeta[socketStatus].label}</span>
      </div>

      <div style={{ marginBottom: "1rem", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <Link to="/messages" style={{ color: "#007bff", textDecoration: "none", fontWeight: "bold" }}>
          &larr; Mesajlarıma Geri Dön
        </Link>
        <span style={{ fontWeight: "bold", fontSize: "1.1rem", color: "#333" }}>{coachName} ile Görüşme</span>
        <button
          onClick={() => setReportTarget({ type: "CONVERSATION", id })}
          style={{
            padding: "0.25rem 0.5rem",
            fontSize: "0.8rem",
            backgroundColor: "transparent",
            color: "#d9534f",
            border: "1px solid #d9534f",
            borderRadius: "4px",
            cursor: "pointer",
          }}
        >
          ⚠️ Görüşmeyi Bildir
        </button>
      </div>

      <FormError error={error} />

      {/* Access Restriction Banner */}
      {!loading && !isMessageAllowed && (
        <div style={{ padding: "0.75rem", border: "1px solid #ffeeba", borderRadius: "8px", backgroundColor: "#fff3cd", color: "#856404", marginBottom: "1rem" }}>
          {isPendingPayment ? (
            <p style={{ margin: 0 }}>
              ⚠️ Ödemeniz henüz tamamlanmadığı için mesajlaşma kullanılamaz.
            </p>
          ) : (
            <p style={{ margin: 0 }}>
              ⚠️ Aboneliğiniz sonlandırıldığı için mesaj gönderemezsiniz.
            </p>
          )}
        </div>
      )}

      {/* Message List Area */}
      <div style={{ flex: 1, border: "1px solid #dee2e6", borderRadius: "8px", backgroundColor: "#fff", padding: "1.5rem", overflowY: "auto", marginBottom: "1rem" }}>
        {loading ? (
          <p>Mesajlar yükleniyor...</p>
        ) : messages.length === 0 && !pendingContent ? (
          <p style={{ fontStyle: "italic", color: "#6c757d", textAlign: "center", marginTop: "2rem" }}>
            Bu görüşmede henüz mesaj bulunmuyor.
          </p>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
            {messages.map((msg) => {
              const isMe = msg.senderId === user?.id;
              return (
                <div
                  key={msg.id}
                  style={{
                    display: "flex",
                    justifyContent: isMe ? "flex-end" : "flex-start",
                  }}
                >
                  <div
                    style={{
                      maxWidth: "70%",
                      padding: "0.75rem 1rem",
                      borderRadius: "12px",
                      backgroundColor: isMe ? "#d4edda" : "#f1f3f5",
                      color: isMe ? "#155724" : "#212529",
                      boxShadow: "0 1px 2px rgba(0,0,0,0.05)",
                    }}
                  >
                    {!isMe && (
                      <span style={{ fontSize: "0.75rem", fontWeight: "bold", display: "block", color: "#6c757d", marginBottom: "0.25rem" }}>
                        {msg.senderName}
                      </span>
                    )}
                    <span style={{ display: "block", wordBreak: "break-word" }}>{msg.content}</span>
                    <span style={{ fontSize: "0.7rem", color: "#6c757d", float: "right", marginTop: "0.25rem" }}>
                      {formatTime(msg.sentAt)}
                      {!isMe && (
                        <button
                          type="button"
                          onClick={() => setReportTarget({ type: "MESSAGE", id: msg.id })}
                          style={{
                            marginLeft: "0.5rem",
                            border: "none",
                            background: "none",
                            color: "#d9534f",
                            cursor: "pointer",
                            fontSize: "0.7rem",
                            padding: 0,
                          }}
                        >
                          Bildir
                        </button>
                      )}
                    </span>
                  </div>
                </div>
              );
            })}
            {pendingContent && (
              <div style={{ display: "flex", justifyContent: "flex-end" }}>
                <div
                  style={{
                    maxWidth: "70%",
                    padding: "0.75rem 1rem",
                    borderRadius: "12px",
                    backgroundColor: "#d4edda",
                    color: "#155724",
                    opacity: 0.6,
                  }}
                >
                  <span style={{ display: "block", wordBreak: "break-word" }}>{pendingContent}</span>
                  <span style={{ fontSize: "0.7rem", color: "#6c757d", float: "right", marginTop: "0.25rem" }}>
                    Gönderiliyor…
                  </span>
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>
        )}
      </div>

      {/* Text Area Input */}
      <form onSubmit={handleSend} style={{ display: "flex", gap: "0.5rem" }}>
        <input
          type="text"
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          placeholder={isMessageAllowed ? "Bir mesaj yazın..." : "Aboneliğiniz devre dışı olduğu için mesaj gönderemezsiniz"}
          disabled={!isMessageAllowed || sendLoading}
          style={{
            flex: 1,
            padding: "0.75rem",
            border: "1px solid #ced4da",
            borderRadius: "4px",
            fontSize: "1rem",
          }}
        />
        <button
          type="submit"
          disabled={!inputText.trim() || sendLoading || !isMessageAllowed}
          style={{
            padding: "0.75rem 1.5rem",
            backgroundColor: !inputText.trim() || !isMessageAllowed ? "#6c757d" : "#28a745",
            color: "white",
            border: "none",
            borderRadius: "4px",
            fontWeight: "bold",
            cursor: !inputText.trim() || sendLoading || !isMessageAllowed ? "not-allowed" : "pointer",
          }}
        >
          {sendLoading ? "Gönderiliyor..." : "Gönder"}
        </button>
      </form>

      {reportTarget && (
        <ReportModal
          targetType={reportTarget.type}
          targetId={reportTarget.id}
          onClose={() => setReportTarget(null)}
        />
      )}
    </div>
  );
};
