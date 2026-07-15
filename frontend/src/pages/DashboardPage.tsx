import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { FormError } from "../components/FormError";
import { PendingPaymentWarning } from "../studentDashboard/PendingPaymentWarning";
import { ActiveSubscriptionCard } from "../studentDashboard/ActiveSubscriptionCard";
import { NoSubscriptionCard } from "../studentDashboard/NoSubscriptionCard";
import { SubscriptionStatusBadge } from "../studentDashboard/SubscriptionStatusBadge";

export const DashboardPage: React.FC = () => {
  const { user, isSuspended } = useAuth();
  const navigate = useNavigate();

  const [data, setData] = useState<StudentDashboardResponse | null>(null);
  const [error, setError] = useState<any | null>(null);
  const [loading, setLoading] = useState<boolean>(false);

  const fetchDashboard = async () => {
    setError(null);
    setLoading(true);
    try {
      const response = await studentDashboardApi.getDashboardData();
      setData(response);
    } catch (err: any) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (isSuspended) {
      navigate("/suspended");
      return;
    }

    if (user) {
      if (user.role === "ADMIN") {
        navigate("/admin");
      } else if (user.role === "STUDENT") {
        fetchDashboard();
      }
    }
  }, [user, isSuspended, navigate]);

  if (loading) {
    return (
      <div style={{ padding: "3rem", textAlign: "center" }}>
        <h3>Dashboard yükleniyor...</h3>
      </div>
    );
  }

  // COACH Role View
  if (user?.role === "COACH") {
    return (
      <div style={{ padding: "2rem" }}>
        <h1>Dashboard</h1>
        <div style={{ padding: "1.5rem", border: "1px solid #ddd", borderRadius: "8px", backgroundColor: "#f9f9f9", marginBottom: "2rem" }}>
          <h3>Kullanıcı Bilgileri</h3>
          <p><strong>Ad Soyad:</strong> {user.fullName}</p>
          <p><strong>E-posta:</strong> {user.email}</p>
          <p><strong>Rol:</strong> Koç (COACH)</p>
        </div>
        <div style={{ padding: "2rem", border: "1px solid #ffeeba", borderRadius: "8px", backgroundColor: "#fff3cd", color: "#856404", textAlign: "center" }}>
          <h4>Koç Paneli Henüz Yayında Değil</h4>
          <p style={{ margin: 0 }}>Koç arayüzü sonraki aşamalarda aktif edilecektir.</p>
        </div>
      </div>
    );
  }

  // STUDENT Role View
  return (
    <div style={{ padding: "2rem", maxWidth: "800px", margin: "0 auto" }}>
      <h1>Dashboard</h1>

      <div style={{ padding: "1.25rem", border: "1px solid #dee2e6", borderRadius: "8px", backgroundColor: "#fff", marginBottom: "2rem" }}>
        <h4 style={{ marginTop: 0, marginBottom: "0.75rem" }}>Öğrenci Bilgileri</h4>
        <div style={{ display: "flex", justifyContent: "space-between" }}>
          <span><strong>Ad Soyad:</strong> {user?.fullName}</span>
          <span><strong>E-posta:</strong> {user?.email}</span>
        </div>
      </div>

      <FormError error={error} />

      {error && (
        <button
          onClick={fetchDashboard}
          style={{ padding: "0.5rem 1rem", backgroundColor: "#007bff", color: "white", border: "none", borderRadius: "4px", cursor: "pointer", marginBottom: "1.5rem" }}
        >
          Yeniden Dene
        </button>
      )}

      {data && (
        <div>
          <h2 style={{ borderBottom: "2px solid #dee2e6", paddingBottom: "0.5rem" }}>Abonelik ve Ödeme Durumu</h2>

          {!data.subscription && <NoSubscriptionCard />}

          {data.subscription && data.subscription.status === "PENDING_PAYMENT" && (
            <div>
              <PendingPaymentWarning payment={data.payment} onRefresh={fetchDashboard} />
              <div style={{ padding: "1.5rem", border: "1px solid #dee2e6", borderRadius: "8px", backgroundColor: "#fff" }}>
                <h4>Abonelik Detayları</h4>
                <p><strong>Koç:</strong> {data.subscription.coachName}</p>
                <p><strong>Paket:</strong> {data.subscription.packageName}</p>
                <p><strong>Durum:</strong> <SubscriptionStatusBadge status={data.subscription.status} /></p>
              </div>
            </div>
          )}

          {data.subscription && (data.subscription.status === "ACTIVE" || data.subscription.status === "PAST_DUE") && (
            <ActiveSubscriptionCard subscription={data.subscription} payment={data.payment} onRefresh={fetchDashboard} />
          )}

          {data.subscription &&
           data.subscription.status !== "ACTIVE" &&
           data.subscription.status !== "PAST_DUE" &&
           data.subscription.status !== "PENDING_PAYMENT" && (
            <div style={{ padding: "1.5rem", border: "1px solid #dee2e6", borderRadius: "8px", backgroundColor: "#fff" }}>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1rem" }}>
                <h3 style={{ margin: 0 }}>Koçluk Aboneliği</h3>
                <SubscriptionStatusBadge status={data.subscription.status} />
              </div>
              <p><strong>Koç:</strong> {data.subscription.coachName}</p>
              <p><strong>Paket:</strong> {data.subscription.packageName}</p>
              {data.subscription.terminationReason && (
                <p style={{ color: "red" }}><strong>Sonlandırma Nedeni:</strong> {data.subscription.terminationReason}</p>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
