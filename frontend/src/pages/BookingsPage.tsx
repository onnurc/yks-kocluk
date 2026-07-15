import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { bookingApi } from "../booking/bookingApi";
import type { SessionResponse } from "../booking/bookingTypes";
import { FormError } from "../components/FormError";

export const BookingsPage: React.FC = () => {
  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<any | null>(null);

  const fetchSessions = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await bookingApi.listMySessions();
      setSessions(data || []);
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSessions();
  }, []);

  const formatDateTime = (isoString: string) => {
    const d = new Date(isoString);
    return d.toLocaleString("tr-TR", {
      day: "numeric",
      month: "long",
      year: "numeric",
      weekday: "long",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const getStatusBadgeStyle = (status: string) => {
    const base = {
      padding: "0.25rem 0.5rem",
      borderRadius: "4px",
      fontSize: "0.8rem",
      fontWeight: "bold" as const,
    };
    switch (status) {
      case "PLANNED":
        return { ...base, backgroundColor: "#cce5ff", color: "#004085" };
      case "COMPLETED":
        return { ...base, backgroundColor: "#d4edda", color: "#155724" };
      case "CANCELLED":
        return { ...base, backgroundColor: "#e2e3e5", color: "#383d41" };
      case "LATE_CANCELLED":
        return { ...base, backgroundColor: "#f8d7da", color: "#721c24" };
      case "NO_SHOW":
        return { ...base, backgroundColor: "#fff3cd", color: "#856404" };
      default:
        return base;
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case "PLANNED":
        return "Planlandı";
      case "COMPLETED":
        return "Tamamlandı";
      case "CANCELLED":
        return "İptal Edildi";
      case "LATE_CANCELLED":
        return "Geç İptal (Kontenjan Yandı)";
      case "NO_SHOW":
        return "Katılmadı";
      default:
        return status;
    }
  };

  return (
    <div style={{ padding: "2rem", maxWidth: "800px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "1.5rem" }}>
        <h1 style={{ margin: 0 }}>Randevularım</h1>
        <Link to="/coaches" style={{ padding: "0.5rem 1rem", backgroundColor: "#007bff", color: "white", textDecoration: "none", borderRadius: "4px", fontWeight: "bold" }}>
          Yeni Randevu Al
        </Link>
      </div>

      <FormError error={error} />

      {loading ? (
        <p>Randevularınız yükleniyor...</p>
      ) : sessions.length === 0 ? (
        <div style={{ padding: "3rem", border: "1px dashed #dee2e6", borderRadius: "8px", textAlign: "center", backgroundColor: "#fff" }}>
          <p style={{ margin: 0, color: "#6c757d", fontSize: "1.1rem" }}>
            Kayıtlı randevunuz bulunmuyor.
          </p>
          <Link to="/coaches" style={{ display: "inline-block", marginTop: "1rem", color: "#007bff", fontWeight: "bold" }}>
            Koçları Keşfet &rarr;
          </Link>
        </div>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
          {sessions.map((session) => (
            <div key={session.id} style={{ padding: "1.5rem", border: "1px solid #dee2e6", borderRadius: "8px", backgroundColor: "#fff", display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "1rem" }}>
              <div>
                <div style={{ display: "flex", alignItems: "center", gap: "0.75rem", marginBottom: "0.5rem" }}>
                  <h3 style={{ margin: 0, color: "#333" }}>{session.coachName}</h3>
                  <span style={getStatusBadgeStyle(session.status)}>
                    {getStatusText(session.status)}
                  </span>
                </div>
                <p style={{ margin: "0.25rem 0", color: "#495057" }}>
                  <strong>Tarih & Saat:</strong> {formatDateTime(session.startTime)}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
