import React from "react";
import { Link, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { LegalDocumentViewer } from "../legal/LegalDocumentViewer";
import { useLegalDocuments } from "../legal/useLegalDocuments";

export const AppLayout: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();
  const footerDocuments = useLegalDocuments(["KVKK_NOTICE", "PRIVACY_POLICY", "TERMS_OF_USE"]);

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", minHeight: "100vh", fontFamily: "sans-serif" }}>
      <header style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "1rem 2rem", backgroundColor: "#343a40", color: "white" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "2rem" }}>
          <h2 style={{ margin: 0 }}>
            <Link to="/dashboard" style={{ color: "white", textDecoration: "none" }}>YKS Koçluk</Link>
          </h2>
          {isAuthenticated && (
            <nav style={{ display: "flex", gap: "1rem" }}>
              <Link to="/dashboard" style={{ color: "#ccc", textDecoration: "none" }}>Anasayfa</Link>
              <Link to="/privacy" style={{ color: "#ccc", textDecoration: "none" }}>Gizlilik</Link>
              {user?.role === "STUDENT" && (
                <>
                  <Link to="/coaches" style={{ color: "#ccc", textDecoration: "none" }}>Koç Keşfet</Link>
                  <Link to="/bookings" style={{ color: "#ccc", textDecoration: "none" }}>Randevularım</Link>
                  <Link to="/messages" style={{ color: "#ccc", textDecoration: "none" }}>Mesajlarım</Link>
                </>
              )}
              {user?.role === "ADMIN" && (
                <Link to="/admin" style={{ color: "#ffc107", textDecoration: "none", fontWeight: "bold" }}>Admin Paneli</Link>
              )}
            </nav>
          )}
        </div>
        <div>
          {isAuthenticated ? (
            <div style={{ display: "flex", alignItems: "center", gap: "1rem" }}>
              <span>{user?.fullName} ({user?.role})</span>
              <button onClick={handleLogout} style={{ padding: "0.5rem 1rem", backgroundColor: "#dc3545", color: "white", border: "none", borderRadius: "4px", cursor: "pointer" }}>
                Çıkış Yap
              </button>
            </div>
          ) : (
            <Link to="/login" style={{ color: "white", textDecoration: "none" }}>Giriş Yap</Link>
          )}
        </div>
      </header>

      <main style={{ flex: 1, backgroundColor: "#f8f9fa" }}>
        <Outlet />
      </main>

      <footer style={{ textAlign: "center", padding: "1rem", backgroundColor: "#e9ecef", borderTop: "1px solid #dee2e6" }}>
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
