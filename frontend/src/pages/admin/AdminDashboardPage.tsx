import React from "react";
import { useAuth } from "../../auth/AuthProvider";

export const AdminDashboardPage: React.FC = () => {
  const { user } = useAuth();

  return (
    <div style={{ padding: "2rem" }}>
      <h1 style={{ color: "#563d7c" }}>Yönetici Paneli (Admin)</h1>
      <p>Hoş geldiniz, <strong>{user?.fullName}</strong>. Sistem yönetici yetkilerine sahipsiniz.</p>

      <div style={{ marginTop: "2rem", display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1.5rem" }}>
        <div style={{ padding: "1.5rem", border: "1px solid #ccc", borderRadius: "8px" }}>
          <h3>Güvenlik Raporları (Safety)</h3>
          <p>Kullanıcı şikayetlerini ve KVKK onay geçmişini buradan inceleyebilirsiniz.</p>
          <button style={{ padding: "0.5rem 1rem", backgroundColor: "#6c757d", color: "white", border: "none", borderRadius: "4px" }} disabled>
            Raporları İncele (Phase 8)
          </button>
        </div>
        <div style={{ padding: "1.5rem", border: "1px solid #ccc", borderRadius: "8px" }}>
          <h3>Finans & İptal İşlemleri</h3>
          <p>İade taleplerini yönetebilir ve aktif koçluk aboneliklerini iptal edebilirsiniz.</p>
          <button style={{ padding: "0.5rem 1rem", backgroundColor: "#6c757d", color: "white", border: "none", borderRadius: "4px" }} disabled>
            Ödemeleri Yönet (Phase 9)
          </button>
        </div>
      </div>
    </div>
  );
};
