import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { safetyApi } from "../../safety/safetyApi";
import type { ReportResponse, ReportStatus } from "../../safety/safetyTypes";
import type { MessageResponse } from "../../messaging/messagingTypes";
import { ApiError } from "../../api/ApiError";

export const AdminSafetyPage: React.FC = () => {
  // State for reports list
  const [reports, setReports] = useState<ReportResponse[]>([]);
  const [filterStatus, setFilterStatus] = useState<string>("");
  const [page, setPage] = useState<number>(0);
  const [totalPages, setTotalPages] = useState<number>(1);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  // State for user suspension modal
  const [suspendUserId, setSuspendUserId] = useState<number | null>(null);
  const [suspendReason, setSuspendReason] = useState<string>("");
  const [isSuspending, setIsSuspending] = useState<boolean>(false);
  const [suspendError, setSuspendError] = useState<string | null>(null);
  const [suspendSuccess, setSuspendSuccess] = useState<string | null>(null);

  // State for conversation access reason modal
  const [accessConvId, setAccessConvId] = useState<number | null>(null);
  const [accessReason, setAccessReason] = useState<string>("");
  const [isLoggingAccess, setIsLoggingAccess] = useState<boolean>(false);
  const [accessError, setAccessError] = useState<string | null>(null);

  // State for active read-only conversation review view
  const [activeReviewConvId, setActiveReviewConvId] = useState<number | null>(null);
  const [reviewReason, setReviewReason] = useState<string>("");
  const [reviewMessages, setReviewMessages] = useState<MessageResponse[]>([]);

  const fetchReports = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await safetyApi.listReports(filterStatus || undefined, page, 10);
      setReports(response.content || []);
      setTotalPages(response.totalPages || 1);
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.detail || "Raporlar yüklenirken bir hata oluştu.");
      } else {
        setError("Sunucuya ulaşılamadı. Lütfen tekrar deneyin.");
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (!activeReviewConvId) {
      fetchReports();
    }
  }, [filterStatus, page, activeReviewConvId]);

  // Handle user suspension submit
  const handleSuspendSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!suspendUserId || isSuspending) return;

    if (!suspendReason.trim()) {
      setSuspendError("Askıya alma gerekçesi boş olamaz.");
      return;
    }

    setIsSuspending(true);
    setSuspendError(null);
    setSuspendSuccess(null);

    try {
      await safetyApi.suspendUser(suspendUserId, suspendReason);
      setSuspendSuccess("Kullanıcı başarıyla askıya alındı.");
      setTimeout(() => {
        setSuspendUserId(null);
        setSuspendReason("");
        setSuspendSuccess(null);
        fetchReports();
      }, 1500);
    } catch (err) {
      if (err instanceof ApiError) {
        setSuspendError(err.detail || "Kullanıcı askıya alınırken bir hata oluştu.");
      } else {
        setSuspendError("Sunucuya ulaşılamadı. Lütfen tekrar deneyin.");
      }
    } finally {
      setIsSuspending(false);
    }
  };

  // Handle audited conversation access submit
  const handleAccessSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!accessConvId || isLoggingAccess) return;

    if (!accessReason.trim()) {
      setAccessError("Erişim gerekçesi boş olamaz.");
      return;
    }

    setIsLoggingAccess(true);
    setAccessError(null);

    try {
      // Fetch messages using the secure audited endpoint passing the reason directly
      const msgPage = await safetyApi.getConversationMessages(accessConvId, accessReason, 0, 100);

      // Success: Save details and transition view
      setReviewReason(accessReason);
      // Backend returns messages newest first (DESC). Reverse to display chronological (ASC).
      setReviewMessages([...(msgPage.content || [])].reverse());
      setActiveReviewConvId(accessConvId);

      // Close modal and reset fields
      setAccessConvId(null);
      setAccessReason("");
    } catch (err) {
      if (err instanceof ApiError) {
        setAccessError(err.detail || "Konuşma detaylarına erişim izni verilmedi.");
      } else {
        setAccessError("Erişim günlüğü kaydedilemedi veya sunucu hatası oluştu.");
      }
    } finally {
      setIsLoggingAccess(false);
    }
  };

  // Convert status codes to Turkish labels
  const getStatusLabel = (status: ReportStatus) => {
    switch (status) {
      case "OPEN":
        return "Açık";
      case "REVIEWED":
        return "İncelendi";
      case "RESOLVED":
        return "Çözüldü";
      case "DISMISSED":
        return "Reddedildi";
      default:
        return status;
    }
  };

  const getStatusStyle = (status: ReportStatus) => {
    switch (status) {
      case "OPEN":
        return { backgroundColor: "#f8d7da", color: "#721c24", border: "1px solid #f5c6cb" };
      case "REVIEWED":
        return { backgroundColor: "#fff3cd", color: "#856404", border: "1px solid #ffeeba" };
      case "RESOLVED":
        return { backgroundColor: "#d4edda", color: "#155724", border: "1px solid #c3e6cb" };
      case "DISMISSED":
        return { backgroundColor: "#e2e8f0", color: "#4a5568", border: "1px solid #cbd5e1" };
    }
  };

  // Render the Read-Only conversation review panel
  if (activeReviewConvId) {
    return (
      <div style={{ padding: "2rem", maxWidth: "900px", margin: "0 auto", fontFamily: "sans-serif" }}>
        <div style={{ marginBottom: "1.5rem" }}>
          <button
            onClick={() => setActiveReviewConvId(null)}
            style={{
              padding: "0.5rem 1rem",
              backgroundColor: "#6c757d",
              color: "white",
              border: "none",
              borderRadius: "6px",
              cursor: "pointer",
              fontWeight: "600",
            }}
          >
            &larr; Güvenlik Paneline Geri Dön
          </button>
        </div>

        <div
          style={{
            padding: "1rem",
            backgroundColor: "#fff3cd",
            border: "1px solid #ffeeba",
            color: "#856404",
            borderRadius: "8px",
            marginBottom: "1.5rem",
            fontWeight: "600",
            fontSize: "0.95rem",
          }}
        >
          🚨 Yönetici İnceleme Görünümü — Salt Okunur
          <div style={{ fontSize: "0.85rem", fontWeight: "normal", marginTop: "0.25rem" }}>
            Erişim Gerekçesi: <em>"{reviewReason}"</em>
          </div>
        </div>

        <div
          style={{
            border: "1px solid #dee2e6",
            borderRadius: "8px",
            backgroundColor: "#fff",
            padding: "1.5rem",
            maxHeight: "60vh",
            overflowY: "auto",
            marginBottom: "1.5rem",
          }}
        >
          {reviewMessages.length === 0 ? (
            <p style={{ fontStyle: "italic", textAlign: "center", color: "#6c757d" }}>
              Bu konuşmada herhangi bir mesaj bulunamadı.
            </p>
          ) : (
            <div style={{ display: "flex", flexDirection: "column", gap: "1rem" }}>
              {reviewMessages.map((msg) => (
                <div
                  key={msg.id}
                  style={{
                    padding: "0.75rem 1rem",
                    backgroundColor: "#f8f9fa",
                    borderRadius: "8px",
                    border: "1px solid #e9ecef",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "0.25rem" }}>
                    <strong style={{ fontSize: "0.9rem", color: "#495057" }}>{msg.senderName}</strong>
                    <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
                      <span style={{ fontSize: "0.75rem", color: "#6c757d" }}>
                        {new Date(msg.createdAt).toLocaleString("tr-TR")}
                      </span>
                      <button
                        onClick={() => setSuspendUserId(msg.senderId)}
                        style={{
                          padding: "0.15rem 0.4rem",
                          fontSize: "0.7rem",
                          backgroundColor: "#dc3545",
                          color: "white",
                          border: "none",
                          borderRadius: "4px",
                          cursor: "pointer",
                        }}
                      >
                        Askıya Al
                      </button>
                    </div>
                  </div>
                  <p style={{ margin: 0, fontSize: "0.95rem", color: "#212529", wordBreak: "break-word" }}>
                    {msg.content}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    );
  }

  return (
    <div style={{ padding: "2rem", fontFamily: "sans-serif", maxWidth: "1200px", margin: "0 auto" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "2rem" }}>
        <h1 style={{ color: "#d9534f", margin: 0, fontWeight: "700" }}>Güvenlik Raporları (Safety Control Panel)</h1>
        <Link
          to="/admin"
          style={{
            textDecoration: "none",
            color: "#6c757d",
            border: "1px solid #6c757d",
            padding: "0.5rem 1rem",
            borderRadius: "6px",
            fontWeight: "600",
          }}
        >
          Admin Dashboard
        </Link>
      </div>

      {error && (
        <div
          style={{
            padding: "1rem",
            backgroundColor: "#f8d7da",
            border: "1px solid #f5c6cb",
            borderRadius: "8px",
            color: "#721c24",
            marginBottom: "1.5rem",
          }}
        >
          {error}
        </div>
      )}

      {/* Filter / Filter section */}
      <div
        style={{
          display: "flex",
          gap: "1rem",
          marginBottom: "1.5rem",
          alignItems: "center",
          backgroundColor: "#f8f9fa",
          padding: "1rem",
          borderRadius: "8px",
          border: "1px solid #dee2e6",
        }}
      >
        <label htmlFor="filter-status" style={{ fontWeight: "600", fontSize: "0.9rem" }}>
          Durum Filtresi:
        </label>
        <select
          id="filter-status"
          value={filterStatus}
          onChange={(e) => {
            setFilterStatus(e.target.value);
            setPage(0);
          }}
          style={{
            padding: "0.5rem",
            borderRadius: "6px",
            border: "1px solid #ced4da",
            backgroundColor: "#fff",
            fontSize: "0.9rem",
          }}
        >
          <option value="">Hepsi</option>
          <option value="OPEN">Açık</option>
          <option value="REVIEWED">İncelendi</option>
          <option value="RESOLVED">Çözüldü</option>
          <option value="DISMISSED">Reddedildi</option>
        </select>
      </div>

      {/* Reports Table */}
      {loading ? (
        <div style={{ textAlign: "center", padding: "3rem" }}>
          <h3>Raporlar yükleniyor...</h3>
        </div>
      ) : reports.length === 0 ? (
        <div style={{ textAlign: "center", padding: "3rem", border: "1px solid #dee2e6", borderRadius: "8px" }}>
          <p style={{ fontStyle: "italic", color: "#6c757d" }}>Kayıtlı güvenlik raporu bulunmamaktadır.</p>
        </div>
      ) : (
        <div style={{ overflowX: "auto", border: "1px solid #dee2e6", borderRadius: "8px" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.9rem" }}>
            <thead>
              <tr style={{ backgroundColor: "#f1f3f5", borderBottom: "2px solid #dee2e6" }}>
                <th style={{ padding: "0.75rem 1rem" }}>ID</th>
                <th style={{ padding: "0.75rem 1rem" }}>Hedef Türü</th>
                <th style={{ padding: "0.75rem 1rem" }}>Hedef ID</th>
                <th style={{ padding: "0.75rem 1rem" }}>Bildiren</th>
                <th style={{ padding: "0.75rem 1rem" }}>Şikayet Nedeni</th>
                <th style={{ padding: "0.75rem 1rem" }}>Durum</th>
                <th style={{ padding: "0.75rem 1rem" }}>Oluşturulma</th>
                <th style={{ padding: "0.75rem 1rem" }}>Aksiyonlar</th>
              </tr>
            </thead>
            <tbody>
              {reports.map((rep) => (
                <tr key={rep.id} style={{ borderBottom: "1px solid #dee2e6" }}>
                  <td style={{ padding: "0.75rem 1rem", fontWeight: "bold" }}>{rep.id}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{rep.targetType}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{rep.targetId}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>{rep.reporterUserId}</td>
                  <td style={{ padding: "0.75rem 1rem" }}>
                    <div style={{ fontWeight: "600" }}>{rep.reason}</div>
                    {rep.details && (
                      <div style={{ fontSize: "0.8rem", color: "#6c757d", marginTop: "0.25rem" }}>{rep.details}</div>
                    )}
                  </td>
                  <td style={{ padding: "0.75rem 1rem" }}>
                    <span
                      style={{
                        padding: "0.25rem 0.5rem",
                        borderRadius: "4px",
                        fontSize: "0.8rem",
                        fontWeight: "600",
                        ...getStatusStyle(rep.status),
                      }}
                    >
                      {getStatusLabel(rep.status)}
                    </span>
                  </td>
                  <td style={{ padding: "0.75rem 1rem" }}>
                    {new Date(rep.createdAt).toLocaleDateString("tr-TR")}
                  </td>
                  <td style={{ padding: "0.75rem 1rem", display: "flex", gap: "0.5rem" }}>
                    {rep.targetType === "USER" && (
                      <button
                        onClick={() => setSuspendUserId(rep.targetId)}
                        style={{
                          padding: "0.35rem 0.75rem",
                          backgroundColor: "#dc3545",
                          color: "white",
                          border: "none",
                          borderRadius: "4px",
                          cursor: "pointer",
                          fontSize: "0.8rem",
                        }}
                      >
                        Askıya Al
                      </button>
                    )}
                    {(rep.targetType === "CONVERSATION" || rep.targetType === "MESSAGE") && (
                      <button
                        onClick={() =>
                          setAccessConvId(
                            rep.targetType === "CONVERSATION"
                              ? rep.targetId
                              : 0 // Wait, message reports will be reviewed via audit access if needed.
                          )
                        }
                        style={{
                          padding: "0.35rem 0.75rem",
                          backgroundColor: "#007bff",
                          color: "white",
                          border: "none",
                          borderRadius: "4px",
                          cursor: "pointer",
                          fontSize: "0.8rem",
                        }}
                      >
                        Konuşmayı İncele
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination controls */}
      {!loading && totalPages > 1 && (
        <div style={{ display: "flex", justifyContent: "center", gap: "1rem", marginTop: "1.5rem" }}>
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => p - 1)}
            style={{
              padding: "0.5rem 1rem",
              borderRadius: "4px",
              border: "1px solid #ccc",
              cursor: page === 0 ? "not-allowed" : "pointer",
            }}
          >
            Önceki
          </button>
          <span style={{ display: "flex", alignItems: "center", fontSize: "0.95rem" }}>
            Sayfa {page + 1} / {totalPages}
          </span>
          <button
            disabled={page >= totalPages - 1}
            onClick={() => setPage((p) => p + 1)}
            style={{
              padding: "0.5rem 1rem",
              borderRadius: "4px",
              border: "1px solid #ccc",
              cursor: page >= totalPages - 1 ? "not-allowed" : "pointer",
            }}
          >
            Sonraki
          </button>
        </div>
      )}

      {/* User Suspension Confirmation Modal */}
      {suspendUserId !== null && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="suspend-modal-title"
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            width: "100%",
            height: "100%",
            backgroundColor: "rgba(0, 0, 0, 0.5)",
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            zIndex: 10000,
          }}
        >
          <div
            style={{
              backgroundColor: "white",
              padding: "2rem",
              borderRadius: "8px",
              width: "100%",
              maxWidth: "450px",
              boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
            }}
          >
            <h2 id="suspend-modal-title" style={{ margin: "0 0 1rem 0", color: "#d9534f" }}>
              Kullanıcı Askıya Alma Onayı
            </h2>

            {suspendSuccess ? (
              <div
                style={{
                  padding: "1rem",
                  backgroundColor: "#d4edda",
                  border: "1px solid #c3e6cb",
                  color: "#155724",
                  borderRadius: "6px",
                  textAlign: "center",
                }}
              >
                {suspendSuccess}
              </div>
            ) : (
              <form onSubmit={handleSuspendSubmit}>
                {suspendError && (
                  <div
                    style={{
                      padding: "0.75rem",
                      backgroundColor: "#f8d7da",
                      border: "1px solid #f5c6cb",
                      color: "#721c24",
                      borderRadius: "6px",
                      marginBottom: "1rem",
                      fontSize: "0.9rem",
                    }}
                  >
                    {suspendError}
                  </div>
                )}
                <p style={{ fontSize: "0.95rem", color: "#495057", marginBottom: "1rem" }}>
                  <strong>ID: {suspendUserId}</strong> olan kullanıcının platform erişimini engellemek üzeresiniz. Bu işlem kullanıcının tüm yetkilerini kısıtlar.
                </p>

                <div style={{ marginBottom: "1.5rem" }}>
                  <label
                    htmlFor="suspend-reason-input"
                    style={{ display: "block", marginBottom: "0.5rem", fontWeight: "600", fontSize: "0.9rem" }}
                  >
                    Askıya Alma Gerekçesi (Zorunlu):
                  </label>
                  <textarea
                    id="suspend-reason-input"
                    value={suspendReason}
                    onChange={(e) => setSuspendReason(e.target.value)}
                    disabled={isSuspending}
                    rows={3}
                    placeholder="Lütfen askıya alma sebebini detaylandırın..."
                    style={{
                      width: "100%",
                      padding: "0.5rem",
                      borderRadius: "4px",
                      border: "1px solid #ccc",
                      fontSize: "0.95rem",
                    }}
                  />
                </div>

                <div style={{ display: "flex", justifyContent: "flex-end", gap: "1rem" }}>
                  <button
                    type="button"
                    onClick={() => {
                      setSuspendUserId(null);
                      setSuspendReason("");
                      setSuspendError(null);
                    }}
                    disabled={isSuspending}
                    style={{
                      padding: "0.5rem 1rem",
                      backgroundColor: "#e2e8f0",
                      color: "#4a5568",
                      border: "none",
                      borderRadius: "6px",
                      cursor: isSuspending ? "not-allowed" : "pointer",
                    }}
                  >
                    Vazgeç
                  </button>
                  <button
                    type="submit"
                    disabled={isSuspending || !suspendReason.trim()}
                    style={{
                      padding: "0.5rem 1.25rem",
                      backgroundColor: "#dc3545",
                      color: "white",
                      border: "none",
                      borderRadius: "6px",
                      cursor: isSuspending || !suspendReason.trim() ? "not-allowed" : "pointer",
                      fontWeight: "600",
                    }}
                  >
                    {isSuspending ? "Askıya alınıyor..." : "Kullanıcıyı Askıya Al"}
                  </button>
                </div>
              </form>
            )}
          </div>
        </div>
      )}

      {/* Admin Conversation Access Reason Modal */}
      {accessConvId !== null && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="access-modal-title"
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            width: "100%",
            height: "100%",
            backgroundColor: "rgba(0, 0, 0, 0.5)",
            display: "flex",
            justifyContent: "center",
            alignItems: "center",
            zIndex: 10000,
          }}
        >
          <div
            style={{
              backgroundColor: "white",
              padding: "2rem",
              borderRadius: "8px",
              width: "100%",
              maxWidth: "450px",
              boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
            }}
          >
            <h2 id="access-modal-title" style={{ margin: "0 0 1rem 0", color: "#007bff" }}>
              Güvenlik İnceleme Gerekçesi
            </h2>
            <form onSubmit={handleAccessSubmit}>
              {accessError && (
                <div
                  style={{
                    padding: "0.75rem",
                    backgroundColor: "#f8d7da",
                    border: "1px solid #f5c6cb",
                    color: "#721c24",
                    borderRadius: "6px",
                    marginBottom: "1rem",
                    fontSize: "0.9rem",
                  }}
                >
                  {accessError}
                </div>
              )}
              <p style={{ fontSize: "0.95rem", color: "#495057", marginBottom: "1rem" }}>
                Çocuk güvenliği veya platform kural ihlallerini denetlemek amacıyla bu konuşma içeriğini incelemek üzeresiniz. Devam etmeden önce yasal denetim gerekçenizi girmeniz zorunludur.
              </p>

              <div style={{ marginBottom: "1.5rem" }}>
                <label
                  htmlFor="access-reason-input"
                  style={{ display: "block", marginBottom: "0.5rem", fontWeight: "600", fontSize: "0.9rem" }}
                >
                  İnceleme Gerekçesi (Zorunlu):
                </label>
                <textarea
                  id="access-reason-input"
                  value={accessReason}
                  onChange={(e) => setAccessReason(e.target.value)}
                  disabled={isLoggingAccess}
                  rows={3}
                  placeholder="Gerekçenizi buraya yazın..."
                  style={{
                    width: "100%",
                    padding: "0.5rem",
                    borderRadius: "4px",
                    border: "1px solid #ccc",
                    fontSize: "0.95rem",
                  }}
                />
              </div>

              <div style={{ display: "flex", justifyContent: "flex-end", gap: "1rem" }}>
                <button
                  type="button"
                  onClick={() => {
                    setAccessConvId(null);
                    setAccessReason("");
                    setAccessError(null);
                  }}
                  disabled={isLoggingAccess}
                  style={{
                    padding: "0.5rem 1rem",
                    backgroundColor: "#e2e8f0",
                    color: "#4a5568",
                    border: "none",
                    borderRadius: "6px",
                    cursor: isLoggingAccess ? "not-allowed" : "pointer",
                  }}
                >
                  Vazgeç
                </button>
                <button
                  type="submit"
                  disabled={isLoggingAccess || !accessReason.trim()}
                  style={{
                    padding: "0.5rem 1.25rem",
                    backgroundColor: "#007bff",
                    color: "white",
                    border: "none",
                    borderRadius: "6px",
                    cursor: isLoggingAccess || !accessReason.trim() ? "not-allowed" : "pointer",
                    fontWeight: "600",
                  }}
                >
                  {isLoggingAccess ? "Onaylanıyor..." : "Gerekçeyi Kaydet ve Aç"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
