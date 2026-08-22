import React from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../auth/AuthProvider";

export const AdminDashboardPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  return (
    <div style={{ padding: "2rem", fontFamily: "sans-serif" }}>
      <h1 style={{ color: "#563d7c" }}>Yönetici Paneli (Admin)</h1>
      <p>Hoş geldiniz, <strong>{user?.fullName}</strong>. Sistem yönetici yetkilerine sahipsiniz.</p>

      <div style={{ marginTop: "2rem", display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1.5rem" }}>
        <div style={{ padding: "1.5rem", border: "1px solid #ccc", borderRadius: "8px", backgroundColor: "#fff" }}>
          <h3>Güvenlik Raporları (Safety)</h3>
          <p>Kullanıcı şikayetlerini ve KVKK onay geçmişini buradan inceleyebilirsiniz.</p>
          <button
            onClick={() => navigate("/admin/safety")}
            style={{
              padding: "0.5rem 1rem",
              backgroundColor: "#d9534f",
              color: "white",
              border: "none",
              borderRadius: "4px",
              cursor: "pointer",
              fontWeight: "600",
            }}
          >
            Raporları İncele
          </button>
        </div>
        <div style={{ padding: "1.5rem", border: "1px solid #ccc", borderRadius: "8px", backgroundColor: "#fff" }}>
          <h3>Finans & İptal İşlemleri</h3>
          <p>İade taleplerini yönetebilir ve aktif koçluk aboneliklerini iptal edebilirsiniz.</p>
          <button
            onClick={() => navigate("/admin/finance")}
            style={{
              padding: "0.5rem 1rem",
              backgroundColor: "#0284c7",
              color: "white",
              border: "none",
              borderRadius: "4px",
              cursor: "pointer",
              fontWeight: "600",
            }}
          >
            Ödemeleri Yönet
          </button>
        </div>
        <div style={{ padding: "1.5rem", border: "1px solid #ccc", borderRadius: "8px", backgroundColor: "#fff" }}>
          <h3>Koç Başvuruları</h3>
          <p>Koç adaylarının başvurularını inceleyip onaylayabilir veya reddedebilirsiniz.</p>
          <button
            onClick={() => navigate("/admin/coach-applications")}
            style={{
              padding: "0.5rem 1rem",
              backgroundColor: "#1a7f37",
              color: "white",
              border: "none",
              borderRadius: "4px",
              cursor: "pointer",
              fontWeight: "600",
            }}
          >
            Başvuruları İncele
          </button>
        </div>
      </div>
    </div>
  );
};
