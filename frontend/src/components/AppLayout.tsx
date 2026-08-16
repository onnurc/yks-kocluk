import React, { useEffect, useState } from "react";
import { Link, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { LegalDocumentViewer } from "../legal/LegalDocumentViewer";
import { useLegalDocuments } from "../legal/useLegalDocuments";
import { messagingApi } from "../messaging/messagingApi";
import { useNotificationSocket } from "../messaging/useNotificationSocket";

const UnreadBadge: React.FC<{ count: number }> = ({ count }) => {
  if (count <= 0) return null;
  return (
    <span
      style={{
        marginLeft: "0.4rem",
        backgroundColor: "#dc3545",
        color: "white",
        borderRadius: "999px",
        padding: "0.05rem 0.45rem",
        fontSize: "0.72rem",
        fontWeight: "bold",
      }}
    >
      {count}
    </span>
  );
};

export const AppLayout: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const footerDocuments = useLegalDocuments(["KVKK_NOTICE", "PRIVACY_POLICY", "TERMS_OF_USE"]);
  const [unreadCount, setUnreadCount] = useState(0);
  const canSeeMessages = isAuthenticated && (user?.role === "STUDENT" || user?.role === "COACH");

  useEffect(() => {
    if (!canSeeMessages) {
      setUnreadCount(0);
      return;
    }
    let cancelled = false;
    const fetchUnreadCount = () => {
      messagingApi
        .listConversations()
        .then((list) => {
          if (!cancelled) {
            setUnreadCount((list || []).reduce((sum, c) => sum + (c.unreadCount || 0), 0));
          }
        })
        .catch(() => {
          // Badge is a nice-to-have; leave it at its last known value on failure.
        });
    };
    fetchUnreadCount();
    // Dispatched by ConversationPage after it successfully marks a conversation read (on
    // open, and whenever a live message arrives while it's being viewed) — refetch so the
    // badge reflects it immediately instead of waiting for the next full page load.
    window.addEventListener("messages-read", fetchUnreadCount);
    return () => {
      cancelled = true;
      window.removeEventListener("messages-read", fetchUnreadCount);
    };
  }, [canSeeMessages]);

  // The other half of the badge: the fetch above only covers messages that already existed when
  // the page loaded (and re-runs on a read). This makes it live — the server pushes an updated
  // total whenever a message arrives, from whatever page the user happens to be on.
  useNotificationSocket({
    enabled: canSeeMessages,
    // Assign, never increment: the server recomputes the authoritative total per push, so two
    // open tabs and a reconnect all converge on the same number instead of drifting apart.
    onNotification: (notification) => setUnreadCount(notification.unreadTotal),
  });

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <div className="app-layout" style={{ display: "flex", flexDirection: "column", minHeight: "100vh", fontFamily: "sans-serif" }}>
      <header className="app-layout__header" style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "1rem 2rem", backgroundColor: "#343a40", color: "white" }}>
        <div className="app-layout__header-primary" style={{ display: "flex", alignItems: "center", gap: "2rem" }}>
          <h2 style={{ margin: 0 }}>
            <Link to="/dashboard" style={{ color: "white", textDecoration: "none" }}>YKS Koçluk</Link>
          </h2>
          {isAuthenticated && (
            <nav style={{ display: "flex", gap: "1rem" }}>
              <Link to="/dashboard" style={{ color: "#ccc", textDecoration: "none" }}>Anasayfa</Link>
              <Link to="/privacy" style={{ color: "#ccc", textDecoration: "none" }}>Gizlilik</Link>
              <Link to="/security" style={{ color: "#ccc", textDecoration: "none" }}>Güvenlik</Link>
              {user?.role === "STUDENT" && (
                <>
                  <Link to="/coaches" style={{ color: "#ccc", textDecoration: "none" }}>Koç Keşfet</Link>
                  <Link to="/bookings" style={{ color: "#ccc", textDecoration: "none" }}>Randevularım</Link>
                  <Link to="/messages" style={{ color: "#ccc", textDecoration: "none", display: "flex", alignItems: "center" }}>
                    Mesajlarım
                    <UnreadBadge count={unreadCount} />
                  </Link>
                </>
              )}
              {user?.role === "COACH" && (
                <Link to="/messages" style={{ color: "#ccc", textDecoration: "none", display: "flex", alignItems: "center" }}>
                  Mesajlarım
                  <UnreadBadge count={unreadCount} />
                </Link>
              )}
              {user?.role === "ADMIN" && (
                <>
                  <Link to="/admin" style={{ color: "#ffc107", textDecoration: "none", fontWeight: "bold" }}>Admin Paneli</Link>
                  <Link to="/admin/messages" style={{ color: "#ccc", textDecoration: "none" }}>Mesaj Gözlemi</Link>
                </>
              )}
            </nav>
          )}
        </div>
        <div>
          {isAuthenticated ? (
            <div className="app-layout__account" style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
              <span className="app-layout__account-name">{user?.fullName} ({user?.role})</span>
              <button onClick={handleLogout} style={{ padding: "0.5rem 1rem", backgroundColor: "#dc3545", color: "white", border: "none", borderRadius: "4px", cursor: "pointer" }}>
                Çıkış Yap
              </button>
            </div>
          ) : (
            <Link to="/login" style={{ color: "white", textDecoration: "none" }}>Giriş Yap</Link>
          )}
        </div>
      </header>

      <main className="app-layout__main" style={{ flex: 1, backgroundColor: "#f8f9fa" }}>
        <Outlet />
      </main>

      <footer className="app-layout__footer" style={{ textAlign: "center", padding: "1rem", backgroundColor: "#e9ecef", borderTop: "1px solid #dee2e6" }}>
        <p style={{ margin: "0 0 .5rem", fontSize: "0.9rem", color: "#6c757d" }}>
          &copy; {new Date().getFullYear()} YKS Koçluk Platformu. Phase 0 Temelleri.
        </p>
        <div style={{ display: "flex", justifyContent: "center", flexWrap: "wrap", gap: "1rem", fontSize: ".85rem" }}>
          <LegalDocumentViewer label="KVKK Aydınlatma Metni" document={footerDocuments.documents.KVKK_NOTICE} loading={footerDocuments.loading} error={footerDocuments.error} onRetry={() => void footerDocuments.reload()} />
          <LegalDocumentViewer label="Gizlilik Politikası" document={footerDocuments.documents.PRIVACY_POLICY} loading={footerDocuments.loading} error={footerDocuments.error} onRetry={() => void footerDocuments.reload()} />
          <LegalDocumentViewer label="Kullanım Koşulları" document={footerDocuments.documents.TERMS_OF_USE} loading={footerDocuments.loading} error={footerDocuments.error} onRetry={() => void footerDocuments.reload()} />
        </div>
      </footer>
    </div>
  );
};
