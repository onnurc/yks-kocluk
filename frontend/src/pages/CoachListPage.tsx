import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";
import type { CoachSummaryResponse } from "../coaches/coachDiscoveryTypes";
import { FormError } from "../components/FormError";

export const CoachListPage: React.FC = () => {
  const [coaches, setCoaches] = useState<CoachSummaryResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<any | null>(null);

  const fetchCoaches = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await coachDiscoveryApi.listCoaches(0, 50);
      setCoaches(response.content || []);
    } catch (err: any) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCoaches();
  }, []);

  if (loading) {
    return (
      <div style={{ padding: "3rem", textAlign: "center" }}>
        <h3>Koçlar yükleniyor...</h3>
      </div>
    );
  }

  return (
    <div style={{ padding: "2rem", maxWidth: "1000px", margin: "0 auto" }}>
      <h1 style={{ marginBottom: "2rem", color: "#333" }}>Koç Keşfet</h1>

      <FormError error={error} />

      {error && (
        <button
          onClick={fetchCoaches}
          style={{
            padding: "0.5rem 1rem",
            backgroundColor: "#007bff",
            color: "white",
            border: "none",
            borderRadius: "4px",
            cursor: "pointer",
            marginBottom: "1.5rem",
          }}
        >
          Yeniden Dene
        </button>
      )}

      {!error && coaches.length === 0 && (
        <div style={{ padding: "2rem", textAlign: "center", border: "1px dashed #ccc", borderRadius: "8px", backgroundColor: "#fff" }}>
          <p style={{ color: "#666", fontSize: "1.1rem" }}>Henüz listelenecek aktif koç bulunmuyor.</p>
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))", gap: "1.5rem" }}>
        {coaches.map((coach) => (
          <div
            key={coach.id}
            style={{
              padding: "1.5rem",
              border: "1px solid #dee2e6",
              borderRadius: "8px",
              backgroundColor: "#fff",
              display: "flex",
              flexDirection: "column",
              justifyContent: "space-between",
              boxShadow: "0 2px 4px rgba(0,0,0,0.05)",
            }}
          >
            <div>
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: "0.5rem" }}>
                <h3 style={{ margin: 0, color: "#0056b3" }}>{coach.fullName}</h3>
                <span
                  style={{
                    padding: "0.25rem 0.5rem",
                    borderRadius: "4px",
                    fontSize: "0.75rem",
                    fontWeight: "bold",
                    backgroundColor: coach.acceptingNewStudents ? "#d4edda" : "#f8d7da",
                    color: coach.acceptingNewStudents ? "#155724" : "#721c24",
                  }}
                >
                  {coach.acceptingNewStudents ? "Aktif" : "Kontenjan Dolu"}
                </span>
              </div>

              <p style={{ margin: "0 0 0.75rem 0", fontStyle: "italic", color: "#666", fontSize: "0.9rem" }}>
                {coach.headline || "YKS Koçu"}
              </p>

              <div style={{ fontSize: "0.9rem", color: "#495057", marginBottom: "1rem" }}>
                <strong>Üniversite:</strong> {coach.universityName || "-"}
              </div>

              {coach.tracks && coach.tracks.length > 0 && (
                <div style={{ display: "flex", flexWrap: "wrap", gap: "0.35rem", marginBottom: "1rem" }}>
                  {coach.tracks.map((track) => (
                    <span
                      key={track}
                      style={{
                        padding: "0.15rem 0.4rem",
                        borderRadius: "20px",
                        backgroundColor: "#e2e3e5",
                        color: "#383d41",
                        fontSize: "0.75rem",
                        fontWeight: "bold",
                      }}
                    >
                      {track}
                    </span>
                  ))}
                </div>
              )}
            </div>

            <div style={{ borderTop: "1px solid #eee", paddingTop: "1rem", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <div style={{ fontSize: "0.85rem", color: "#6c757d" }}>
                <span>⭐ {coach.rating != null ? coach.rating.toFixed(1) : "Yeni"}</span>
                <span style={{ margin: "0 0.5rem" }}>|</span>
                <span>{coach.totalSessions} Seans</span>
              </div>

              <Link
                to={`/coaches/${coach.id}`}
                style={{
                  padding: "0.4rem 0.8rem",
                  backgroundColor: "#007bff",
                  color: "white",
                  textDecoration: "none",
                  borderRadius: "4px",
                  fontSize: "0.9rem",
                  fontWeight: "bold",
                  textAlign: "center",
                }}
              >
                Detayları Gör
              </Link>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
