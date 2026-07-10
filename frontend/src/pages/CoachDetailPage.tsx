import React, { useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";
import type { CoachDetailResponse, PackageResponse } from "../coaches/coachDiscoveryTypes";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { FormError } from "../components/FormError";
import { CheckoutSection } from "../subscriptionCheckout/CheckoutSection";
import { BookingSection } from "../booking/BookingSection";
import { canMessageWithSubscription } from "../access/subscriptionAccess";
import { messagingApi } from "../messaging/messagingApi";

export const CoachDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const coachId = Number(id);

  const { user } = useAuth();

  const [coach, setCoach] = useState<CoachDetailResponse | null>(null);
  const [packages, setPackages] = useState<PackageResponse[]>([]);
  const [dashboardData, setDashboardData] = useState<StudentDashboardResponse | null>(null);

  const [selectedPackage, setSelectedPackage] = useState<PackageResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<any | null>(null);

  const navigate = useNavigate();
  const [msgLoading, setMsgLoading] = useState<boolean>(false);

  const handleOpenConversation = async () => {
    if (msgLoading) return;
    setMsgLoading(true);
    try {
      const response = await messagingApi.openConversation(coachId);
      navigate(`/messages/${response.id}`);
    } catch (err: any) {
      alert("Mesajlaşma başlatılamadı.");
    } finally {
      setMsgLoading(false);
    }
  };

  const sub = dashboardData?.subscription;
  const isSubscribedToThisCoach = sub?.coachId === coachId;
  const isMessageAllowed = isSubscribedToThisCoach && canMessageWithSubscription(sub?.status);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [coachDetail, packageList] = await Promise.all([
        coachDiscoveryApi.getCoachDetail(coachId),
        coachDiscoveryApi.listPackages(),
      ]);
      setCoach(coachDetail);
      setPackages(packageList || []);

      // If user is a STUDENT, fetch dashboard/subscription state
      if (user?.role === "STUDENT") {
        try {
          const dash = await studentDashboardApi.getDashboardData();
          setDashboardData(dash);
        } catch {
          // Gracefully continue without subscription info if it fails
        }
      }
    } catch (err: any) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (coachId) {
      loadData();
    }
  }, [coachId, user]);

  if (loading) {
    return (
      <div style={{ padding: "3rem", textAlign: "center" }}>
        <h3>Koç detayları yükleniyor...</h3>
      </div>
    );
  }

  if (error || !coach) {
    return (
      <div style={{ padding: "2rem", maxWidth: "800px", margin: "0 auto" }}>
        <FormError error={error || new Error("Koç bulunamadı.")} />
        <Link
          to="/coaches"
          style={{
            display: "inline-block",
            padding: "0.5rem 1rem",
            backgroundColor: "#6c757d",
            color: "white",
            textDecoration: "none",
            borderRadius: "4px",
            marginTop: "1rem",
          }}
        >
          Geri Dön
        </Link>
      </div>
    );
  }


  return (
    <div style={{ padding: "2rem", maxWidth: "800px", margin: "0 auto" }}>
      <div style={{ marginBottom: "1.5rem" }}>
        <Link to="/coaches" style={{ color: "#007bff", textDecoration: "none", fontWeight: "bold" }}>
          &larr; Koç Keşfet Listesine Geri Dön
        </Link>
      </div>

      <div style={{ padding: "2rem", border: "1px solid #dee2e6", borderRadius: "8px", backgroundColor: "#fff", marginBottom: "2rem" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "1rem" }}>
          <div>
            <h1 style={{ margin: "0 0 0.5rem 0", color: "#333" }}>{coach.fullName}</h1>
            <p style={{ margin: 0, fontSize: "1.1rem", fontStyle: "italic", color: "#666" }}>
              {coach.headline || "YKS Koçu"}
            </p>
          </div>
          <span
            style={{
              padding: "0.35rem 0.75rem",
              borderRadius: "4px",
              fontSize: "0.85rem",
              fontWeight: "bold",
              backgroundColor: coach.acceptingNewStudents ? "#d4edda" : "#f8d7da",
              color: coach.acceptingNewStudents ? "#155724" : "#721c24",
            }}
          >
            {coach.acceptingNewStudents ? "Aktif" : "Kontenjan Dolu"}
          </span>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem", marginBottom: "1.5rem", fontSize: "0.95rem" }}>
          <div>
            <p style={{ margin: "0.4rem 0" }}><strong>Üniversite:</strong> {coach.universityName}</p>
            <p style={{ margin: "0.4rem 0" }}><strong>Bölüm:</strong> {coach.department}</p>
            <p style={{ margin: "0.4rem 0" }}><strong>Mezuniyet Yılı:</strong> {coach.graduationYear}</p>
          </div>
          <div>
            <p style={{ margin: "0.4rem 0" }}><strong>Puanı:</strong> ⭐ {coach.rating != null ? coach.rating.toFixed(1) : "Yeni"}</p>
            <p style={{ margin: "0.4rem 0" }}><strong>Toplam Seans:</strong> {coach.totalSessions} Seans</p>
          </div>
        </div>

        {user?.role === "STUDENT" && (
          <div style={{ borderTop: "1px solid #eee", paddingTop: "1rem", paddingBottom: "0.5rem" }}>
            {isMessageAllowed ? (
              <button
                disabled={msgLoading}
                onClick={handleOpenConversation}
                style={{
                  padding: "0.5rem 1.25rem",
                  backgroundColor: "#007bff",
                  color: "white",
                  border: "none",
                  borderRadius: "4px",
                  fontWeight: "bold",
                  cursor: msgLoading ? "not-allowed" : "pointer",
                }}
              >
                {msgLoading ? "Sohbet Açılıyor..." : "💬 Mesaj Gönder"}
              </button>
            ) : (
              <p style={{ margin: 0, color: "#856404", fontSize: "0.9rem", fontStyle: "italic" }}>
                🔒 Mesaj göndermek için bu koç ile aktif veya geçmiş bir aboneliğiniz olmalıdır.
              </p>
            )}
          </div>
        )}

        {coach.tracks && coach.tracks.length > 0 && (
          <div style={{ marginBottom: "1.5rem" }}>
            <strong style={{ display: "block", marginBottom: "0.5rem" }}>Uzmanlık Alanları (Alanlar):</strong>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "0.4rem" }}>
              {coach.tracks.map((track) => (
                <span
                  key={track}
                  style={{
                    padding: "0.2rem 0.5rem",
                    borderRadius: "20px",
                    backgroundColor: "#e2e3e5",
                    color: "#383d41",
                    fontSize: "0.8rem",
                    fontWeight: "bold",
                  }}
                >
                  {track}
                </span>
              ))}
            </div>
          </div>
        )}

        <div style={{ borderTop: "1px solid #eee", paddingTop: "1.5rem" }}>
          <h3 style={{ marginTop: 0 }}>Hakkında</h3>
          <p style={{ margin: 0, lineHeight: "1.6", color: "#495057", whiteSpace: "pre-line" }}>{coach.bio || "Biyografi belirtilmemiş."}</p>
        </div>
      </div>

      <h2 style={{ marginBottom: "1rem" }}>Abonelik Paketleri</h2>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "1rem", marginBottom: "2rem" }}>
        {packages.map((pkg) => {
          const isSelected = selectedPackage?.id === pkg.id;
          return (
            <div
              key={pkg.id}
              onClick={() => setSelectedPackage(pkg)}
              style={{
                padding: "1.5rem",
                border: isSelected ? "2px solid #007bff" : "1px solid #dee2e6",
                borderRadius: "8px",
                backgroundColor: isSelected ? "#f1f8ff" : "#fff",
                cursor: "pointer",
                transition: "all 0.2s ease",
                boxShadow: isSelected ? "0 4px 12px rgba(0,123,255,0.15)" : "0 2px 4px rgba(0,0,0,0.05)",
                display: "flex",
                flexDirection: "column",
                justifyContent: "space-between",
              }}
            >
              <div>
                <h4 style={{ margin: "0 0 0.5rem 0", color: "#333" }}>{pkg.name}</h4>
                <div style={{ fontSize: "1.25rem", fontWeight: "bold", color: "#007bff", marginBottom: "1rem" }}>
                  {pkg.price} TRY
                </div>
                <div style={{ fontSize: "0.9rem", color: "#6c757d", marginBottom: "1rem" }}>
                  <p style={{ margin: "0.25rem 0" }}>📅 {pkg.durationDays} Gün Geçerlilik</p>
                  <p style={{ margin: "0.25rem 0" }}>🗣️ Haftada {pkg.weeklySessions} Görüşme</p>
                </div>
              </div>
              <button
                style={{
                  width: "100%",
                  padding: "0.5rem",
                  backgroundColor: isSelected ? "#007bff" : "#f8f9fa",
                  color: isSelected ? "white" : "#495057",
                  border: "1px solid #ced4da",
                  borderRadius: "4px",
                  fontWeight: "bold",
                  cursor: "pointer",
                }}
              >
                {isSelected ? "Seçildi" : "Paket Seç"}
              </button>
            </div>
          );
        })}
      </div>

      {selectedPackage && (
        <CheckoutSection
          coachId={coach.id}
          coachName={coach.fullName}
          packageId={selectedPackage.id}
          packageName={selectedPackage.name}
          price={selectedPackage.price}
          dashboardData={dashboardData}
        />
      )}

      {user?.role === "STUDENT" && (
        <BookingSection
          coachId={coach.id}
          dashboardData={dashboardData}
        />
      )}
    </div>
  );
};
