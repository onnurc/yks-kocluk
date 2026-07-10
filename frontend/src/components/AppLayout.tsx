import React from "react";
import { Link, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { KvkkConsentModal } from "../safety/KvkkConsentModal";

export const AppLayout: React.FC = () => {
  const { user, isAuthenticated, logout, hasConsented } = useAuth();
  const navigate = useNavigate();

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
        {!hasConsented && isAuthenticated ? <KvkkConsentModal /> : <Outlet />}
      </main>

      <footer style={{ textAlign: "center", padding: "1rem", backgroundColor: "#e9ecef", borderTop: "1px solid #dee2e6" }}>
        <p style={{ margin: 0, fontSize: "0.9rem", color: "#6c757d" }}>
          &copy; {new Date().getFullYear()} YKS Koçluk Platformu. Phase 0 Temelleri.
        </p>
      </footer>
    </div>
  );
};
