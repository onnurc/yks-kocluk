import React from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";

export const SuspendedPage: React.FC = () => {
  const { logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <div style={{ maxWidth: "500px", margin: "6rem auto", padding: "3rem", border: "1px solid #ffccd5", borderRadius: "8px", backgroundColor: "#fff5f5", textAlign: "center" }}>
      <h1 style={{ color: "#d9534f" }}>Hesap Askıya Alındı</h1>
      <p style={{ margin: "1.5rem 0", fontSize: "1.1rem" }}>
        Güvenlik kurallarının veya platform ilkelerinin ihlali nedeniyle hesabınız askıya alınmıştır.
      </p>
      <p style={{ color: "#666", fontSize: "0.9rem", marginBottom: "2rem" }}>
        Daha fazla bilgi edinmek veya itiraz etmek için lütfen destek ekibiyle iletişime geçin.
      </p>
      <button onClick={handleLogout} style={{ padding: "0.75rem 1.5rem", backgroundColor: "#d9534f", color: "white", border: "none", borderRadius: "4px", cursor: "pointer", fontSize: "1rem" }}>
        Çıkış Yap
      </button>
    </div>
  );
};
