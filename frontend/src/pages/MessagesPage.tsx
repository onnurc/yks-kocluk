import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { messagingApi } from "../messaging/messagingApi";
import type { ConversationResponse } from "../messaging/messagingTypes";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { canMessageWithSubscription } from "../access/subscriptionAccess";
import { FormError } from "../components/FormError";

export const MessagesPage: React.FC = () => {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isStudent = user?.role === "STUDENT";
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [dashboardData, setDashboardData] = useState<StudentDashboardResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<any | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      // The subscription-gated access banner below only applies to students — a coach's
      // ability to read/send isn't tied to a subscription (MessageService.sendMessage only
      // checks it for the student sender), so skip the student-only dashboard call for coaches.
      const [convList, dash] = await Promise.all([
        messagingApi.listConversations(),
        isStudent ? studentDashboardApi.getDashboardData() : Promise.resolve(null),
      ]);
      setConversations(convList || []);
      setDashboardData(dash);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const formatTimestamp = (isoString: string | null) => {
    if (!isoString) return "";
    const d = new Date(isoString);
    return d.toLocaleString("tr-TR", {
      day: "numeric",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const hasAccess = isStudent ? canMessageWithSubscription(dashboardData?.subscription?.status) : true;

  return (
    <div style={{ padding: "2rem", maxWidth: "800px", margin: "0 auto" }}>
      <h1 style={{ marginBottom: "1.5rem" }}>Mesajlarım</h1>

      {!loading && !hasAccess && (
        <div style={{ padding: "1rem", border: "1px solid #ffeeba", borderRadius: "8px", backgroundColor: "#fff3cd", color: "#856404", marginBottom: "1.5rem" }}>
          ⚠️ Mesaj göndermek veya almak için aktif bir aboneliğiniz olmalıdır.
          <div style={{ marginTop: "0.5rem" }}>
            <Link to="/coaches" style={{ color: "#856404", fontWeight: "bold", textDecoration: "underline" }}>
              Koç Keşfet Sayfasına Git &rarr;
            </Link>
          </div>
        </div>
      )}

      <FormError error={error} />

      {loading ? (
        <p>Mesajlaşmalarınız yükleniyor...</p>
      ) : conversations.length === 0 ? (
        <div style={{ padding: "3rem", border: "1px dashed #dee2e6", borderRadius: "8px", textAlign: "center", backgroundColor: "#fff" }}>
          <p style={{ margin: 0, color: "#6c757d", fontSize: "1.1rem" }}>
            Henüz başlatılmış bir mesajlaşmanız bulunmuyor.
          </p>
          {isStudent ? (
            <>
              {hasAccess && (
                <p style={{ margin: "0.5rem 0 0 0", fontSize: "0.95rem" }}>
                  Mesaj göndermek için abonesi olduğunuz koçun detay sayfasına giderek <strong>"Mesaj Gönder"</strong> butonunu kullanabilirsiniz.
                </p>
              )}
              <Link to="/coaches" style={{ display: "inline-block", marginTop: "1rem", color: "#007bff", fontWeight: "bold" }}>
                Koçları Listele &rarr;
              </Link>
            </>
          ) : (
            <p style={{ margin: "0.5rem 0 0 0", fontSize: "0.95rem" }}>
              Bir öğrenci size mesaj gönderdiğinde konuşma burada görünecek.
            </p>
          )}
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "0.75rem" }}>
          {conversations.map((conv) => (
            <div
              key={conv.id}
              onClick={() => navigate(`/messages/${conv.id}`)}
              style={{
                padding: "1.25rem",
                border: "1px solid #dee2e6",
                borderRadius: "8px",
                backgroundColor: "#fff",
                cursor: "pointer",
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                transition: "background-color 0.15s ease",
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = "#f8f9fa")}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = "#fff")}
            >
              <div>
                <h3 style={{ margin: "0 0 0.25rem 0", color: "#333" }}>{isStudent ? conv.coachName : conv.studentName}</h3>
                <span style={{ fontSize: "0.8rem", color: "#6c757d" }}>
                  Son Mesajlaşma: {formatTimestamp(conv.lastMessageAt)}
                </span>
                <div style={{ marginTop: "0.4rem", fontSize: "0.78rem", color: "#6c757d" }}>
                  {conv.observer.displayName} · Salt okunur gözlemci
                </div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "0.5rem" }}>
                <span style={{ color: "#ccc", fontSize: "1.25rem" }}>&rsaquo;</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
