import React, { useEffect, useState, useRef } from "react";
import { useParams, Link } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { messagingApi } from "../messaging/messagingApi";
import type { MessageResponse } from "../messaging/messagingTypes";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { canMessageWithSubscription } from "../access/subscriptionAccess";
import { FormError } from "../components/FormError";

export const ConversationPage: React.FC = () => {
  const { conversationId } = useParams<{ conversationId: string }>();
  const id = Number(conversationId);
  const { user } = useAuth();

  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [dashboardData, setDashboardData] = useState<StudentDashboardResponse | null>(null);
  const [inputText, setInputText] = useState<string>("");
  const [loading, setLoading] = useState<boolean>(true);
  const [sendLoading, setSendLoading] = useState<boolean>(false);
  const [error, setError] = useState<any | null>(null);

  const messagesEndRef = useRef<HTMLDivElement | null>(null);

  const loadMessagesAndDetails = async () => {
    setError(null);
    try {
      const [msgPage, dash] = await Promise.all([
        messagingApi.listMessages(id, 0, 100),
        studentDashboardApi.getDashboardData(),
      ]);
      // The API returns messages in descending order (newest first).
      // We reverse them to display in standard chronological order (oldest first).
      const sorted = [...(msgPage.content || [])].reverse();
      setMessages(sorted);
      setDashboardData(dash);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (id) {
      loadMessagesAndDetails();
    }
  }, [id]);

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanText = inputText.trim();
    if (!cleanText || sendLoading) return;

    const subStatus = dashboardData?.subscription?.status;
    if (!canMessageWithSubscription(subStatus)) {
      setError(new Error("Mesaj göndermek için geçerli bir aboneliğiniz olmalıdır."));
      return;
    }

    setSendLoading(true);
    setError(null);

    try {
      const response = await messagingApi.sendMessage(id, cleanText);
      setMessages((prev) => [...prev, response]);
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
  const isMessageAllowed = canMessageWithSubscription(subStatus);
  const isPendingPayment = subStatus === "PENDING_PAYMENT";

  // Determine coach name for page title
  const coachName = messages.length > 0
    ? (messages[0].senderId === user?.id ? "" : messages[0].senderName)
    : "Koçunuz";

  return (
    <div style={{ padding: "2rem", maxWidth: "800px", margin: "0 auto", display: "flex", flexDirection: "column", height: "calc(100vh - 120px)" }}>
      <div style={{ marginBottom: "1rem", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <Link to="/messages" style={{ color: "#007bff", textDecoration: "none", fontWeight: "bold" }}>
          &larr; Mesajlarıma Geri Dön
        </Link>
        <span style={{ fontWeight: "bold", fontSize: "1.1rem", color: "#333" }}>{coachName} ile Görüşme</span>
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
        ) : messages.length === 0 ? (
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
                      {formatTime(msg.createdAt)}
                    </span>
                  </div>
                </div>
              );
            })}
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
    </div>
  );
};
