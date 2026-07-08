import React from "react";
import { useAuth } from "../auth/AuthProvider";

export const DashboardPage: React.FC = () => {
  const { user } = useAuth();

  return (
    <div style={{ padding: "2rem" }}>
      <h1>Dashboard</h1>
      <div style={{ padding: "1.5rem", border: "1px solid #ddd", borderRadius: "8px", backgroundColor: "#f9f9f9" }}>
        <h3>Kullanıcı Bilgileri</h3>
        <p><strong>Ad Soyad:</strong> {user?.fullName}</p>
        <p><strong>E-posta:</strong> {user?.email}</p>
        <p><strong>Rol:</strong> {user?.role}</p>
        <p><strong>Durum:</strong> {user?.status}</p>
      </div>

      <div style={{ marginTop: "2rem" }}>
        <h2>Abonelik Durumu (Öğrenci)</h2>
        <p>Abonelik modülü Phase 2 kapsamında entegre edilecektir. Şu an için altyapı hazırlığı tamamlanmıştır.</p>
      </div>
    </div>
  );
};
