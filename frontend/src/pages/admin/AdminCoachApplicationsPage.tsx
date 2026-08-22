import React, { useCallback, useEffect, useState } from "react";
import { coachApplicationAdminApi } from "../../coachApplications/coachApplicationAdminApi";
import type { CoachApplicationResponse, CoachApplicationStatus } from "../../coachApplications/coachApplicationTypes";
import { ApiError } from "../../api/ApiError";

const STATUS_LABELS: Record<CoachApplicationStatus, string> = {
  PENDING: "Bekliyor",
  APPROVED: "Onaylandı",
  REJECTED: "Reddedildi",
};

export const AdminCoachApplicationsPage: React.FC = () => {
  const [status, setStatus] = useState<CoachApplicationStatus>("PENDING");
  const [applications, setApplications] = useState<CoachApplicationResponse[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionSuccess, setActionSuccess] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [rejectingId, setRejectingId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState("");

  const fetchApplications = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await coachApplicationAdminApi.list(status, page);
      setApplications(response.content);
      setTotalPages(response.totalPages || 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail : "Başvurular yüklenirken bir hata oluştu.");
    } finally {
      setLoading(false);
    }
  }, [status, page]);

  useEffect(() => {
    fetchApplications();
  }, [fetchApplications]);

  const handleApprove = async (id: number) => {
    setBusyId(id);
    setActionError(null);
    setActionSuccess(null);
    try {
      await coachApplicationAdminApi.approve(id);
      setActionSuccess("Hesap oluşturuldu. Şifre belirleme bağlantısı adaya e-posta ile gönderildi.");
      await fetchApplications();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.detail : "Onaylama sırasında bir hata oluştu.");
    } finally {
      setBusyId(null);
    }
  };

  const handleRejectSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (rejectingId == null) return;
    if (!rejectReason.trim()) {
      setActionError("Red gerekçesi boş olamaz.");
      return;
    }
    setBusyId(rejectingId);
    setActionError(null);
    setActionSuccess(null);
    try {
      await coachApplicationAdminApi.reject(rejectingId, rejectReason.trim());
      setActionSuccess("Başvuru reddedildi.");
      setRejectingId(null);
      setRejectReason("");
      await fetchApplications();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.detail : "Reddetme sırasında bir hata oluştu.");
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ padding: "2rem", fontFamily: "sans-serif" }}>
      <h1 style={{ color: "#563d7c" }}>Koç Başvuruları</h1>

      <div style={{ margin: "1rem 0", display: "flex", gap: "0.5rem" }}>
        {(Object.keys(STATUS_LABELS) as CoachApplicationStatus[]).map((s) => (
          <button
            key={s}
            onClick={() => { setStatus(s); setPage(0); }}
            style={{
              padding: "0.4rem 0.9rem",
              borderRadius: "999px",
              border: s === status ? "2px solid #563d7c" : "1px solid #ccc",
              background: s === status ? "#f3eefc" : "#fff",
              fontWeight: s === status ? 700 : 400,
              cursor: "pointer",
            }}
          >
            {STATUS_LABELS[s]}
          </button>
        ))}
      </div>

      {actionSuccess && <p style={{ color: "#1a7f37" }}>{actionSuccess}</p>}
      {actionError && <p style={{ color: "#d9534f" }}>{actionError}</p>}
      {error && <p style={{ color: "#d9534f" }}>{error}</p>}
      {loading && <p>Yükleniyor…</p>}

      {!loading && applications.length === 0 && <p>Bu durumda başvuru yok.</p>}

      <div style={{ display: "flex", flexDirection: "column", gap: "1rem", marginTop: "1rem" }}>
        {applications.map((app) => (
          <div key={app.id} style={{ padding: "1.25rem", border: "1px solid #ccc", borderRadius: "8px", backgroundColor: "#fff" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
              <div>
                <strong>{app.fullName}</strong>
                <div style={{ color: "#555" }}>{app.email}{app.phone ? ` · ${app.phone}` : ""}</div>
              </div>
              <span style={{ fontSize: "0.85rem", color: "#777" }}>{new Date(app.createdAt).toLocaleString("tr-TR")}</span>
            </div>
            {app.experience && <p style={{ marginTop: "0.75rem", whiteSpace: "pre-wrap" }}>{app.experience}</p>}
            {app.status === "REJECTED" && app.reviewNote && (
              <p style={{ marginTop: "0.5rem", color: "#d9534f" }}>Red gerekçesi: {app.reviewNote}</p>
            )}
            {app.status === "APPROVED" && (
              <p style={{ marginTop: "0.5rem", color: "#1a7f37" }}>Hesap oluşturuldu (kullanıcı #{app.linkedUserId}).</p>
            )}

            {app.status === "PENDING" && (
              <div style={{ marginTop: "1rem", display: "flex", gap: "0.75rem" }}>
                <button
                  onClick={() => handleApprove(app.id)}
                  disabled={busyId === app.id}
                  style={{ padding: "0.5rem 1rem", backgroundColor: "#1a7f37", color: "#fff", border: "none", borderRadius: "4px", fontWeight: 600, cursor: "pointer" }}
                >
                  Onayla ve Hesap Oluştur
                </button>
                <button
                  onClick={() => { setRejectingId(app.id); setRejectReason(""); setActionError(null); }}
                  disabled={busyId === app.id}
                  style={{ padding: "0.5rem 1rem", backgroundColor: "#d9534f", color: "#fff", border: "none", borderRadius: "4px", fontWeight: 600, cursor: "pointer" }}
                >
                  Reddet
                </button>
              </div>
            )}

            {rejectingId === app.id && (
              <form onSubmit={handleRejectSubmit} style={{ marginTop: "1rem", display: "flex", flexDirection: "column", gap: "0.5rem" }}>
                <label htmlFor={`reject-reason-${app.id}`}>Red gerekçesi:</label>
                <textarea
                  id={`reject-reason-${app.id}`}
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  rows={3}
                  required
                />
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <button type="submit" disabled={busyId === app.id} style={{ padding: "0.4rem 1rem", cursor: "pointer" }}>Red Gerekçesini Gönder</button>
                  <button type="button" onClick={() => setRejectingId(null)} style={{ padding: "0.4rem 1rem", cursor: "pointer" }}>Vazgeç</button>
                </div>
              </form>
            )}
          </div>
        ))}
      </div>

      {totalPages > 1 && (
        <div style={{ marginTop: "1.5rem", display: "flex", gap: "0.5rem", alignItems: "center" }}>
          <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Önceki</button>
          <span>Sayfa {page + 1} / {totalPages}</span>
          <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>Sonraki</button>
        </div>
      )}
    </div>
  );
};
