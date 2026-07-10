import React, { useEffect, useState } from "react";
import { financeApi } from "../../safety/financeApi";
import type { AdminPaymentResponse, AdminSubscriptionResponse } from "../../safety/financeTypes";
import { ApiError } from "../../api/ApiError";

export const AdminFinancePage: React.FC = () => {
  const [payments, setPayments] = useState<AdminPaymentResponse[]>([]);
  const [subscriptions, setSubscriptions] = useState<AdminSubscriptionResponse[]>([]);

  // Pagination
  const [paymentPage, setPaymentPage] = useState(0);
  const [paymentTotal, setPaymentTotal] = useState(0);
  const [subPage, setSubPage] = useState(0);
  const [subTotal, setSubTotal] = useState(0);

  // Loading & Error States
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Modals state
  const [refundPayment, setRefundPayment] = useState<AdminPaymentResponse | null>(null);
  const [refundAmount, setRefundAmount] = useState("");
  const [refundReason, setRefundReason] = useState("");
  const [isRefunding, setIsRefunding] = useState(false);
  const [refundError, setRefundError] = useState<string | null>(null);
  const [refundResult, setRefundResult] = useState<{ amount: number; remaining: number } | null>(null);

  const [terminateSub, setTerminateSub] = useState<AdminSubscriptionResponse | null>(null);
  const [terminateReason, setTerminateReason] = useState("");
  const [isTerminating, setIsTerminating] = useState(false);
  const [terminateError, setTerminateError] = useState<string | null>(null);
  const [terminateResult, setTerminateResult] = useState<{ subId: number; status: string } | null>(null);

  const pageSize = 10;

  const loadData = async () => {
    setIsLoading(true);
    setErrorMsg(null);
    try {
      const pRes = await financeApi.listPayments(paymentPage, pageSize);
      setPayments(pRes.content);
      setPaymentTotal(pRes.totalElements);

      const sRes = await financeApi.listSubscriptions(subPage, pageSize);
      setSubscriptions(sRes.content);
      setSubTotal(sRes.totalElements);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMsg(err.message || "Finans verileri yüklenirken bir hata oluştu.");
      } else {
        setErrorMsg("Sunucuya ulaşılamadı. Lütfen tekrar deneyin.");
      }
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [paymentPage, subPage]);

  // Handler for Refund Submit
  const handleRefundSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!refundPayment || isRefunding) return;

    const amountNum = parseFloat(refundAmount);
    if (isNaN(amountNum) || amountNum <= 0) {
      setRefundError("Lütfen geçerli pozitif bir iade tutarı girin.");
      return;
    }

    if (amountNum > refundPayment.remainingRefundableAmount) {
      setRefundError(`İade tutarı kalan iade edilebilir tutarı (${refundPayment.remainingRefundableAmount} TRY) aşamaz.`);
      return;
    }

    setIsRefunding(true);
    setRefundError(null);

    try {
      const res = await financeApi.refund(refundPayment.id, amountNum, refundReason);
      setRefundResult({
        amount: res.amount,
        remaining: res.remainingRefundableAmount,
      });
      // reload lists
      loadData();
      // clean state
      setRefundAmount("");
      setRefundReason("");
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "INVALID_REFUND_AMOUNT") {
          setRefundError("Geçersiz iade tutarı.");
        } else if (err.code === "EXCEEDS_REFUNDABLE_AMOUNT") {
          setRefundError("İade tutarı kalan iade edilebilir tutarı aşmaktadır.");
        } else if (err.code === "PROVIDER_REFUND_FAILED") {
          setRefundError(`Ödeme sağlayıcı iade hatası: ${err.message}`);
        } else {
          setRefundError(err.message || "İade işlemi sırasında bir hata oluştu.");
        }
      } else {
        setRefundError("Sunucuya ulaşılamadı. Lütfen tekrar deneyin.");
      }
    } finally {
      setIsRefunding(false);
    }
  };

  // Handler for Termination Submit
  const handleTerminateSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!terminateSub || isTerminating) return;

    if (!terminateReason.trim()) {
      setTerminateError("Lütfen sonlandırma gerekçesini girin.");
      return;
    }

    setIsTerminating(true);
    setTerminateError(null);

    try {
      const res = await financeApi.terminateSubscription(terminateSub.id, terminateReason);
      setTerminateResult({
        subId: res.subscriptionId,
        status: res.status,
      });
      // reload lists
      loadData();
      // clean state
      setTerminateReason("");
    } catch (err) {
      if (err instanceof ApiError) {
        setTerminateError(err.message || "Abonelik sonlandırılırken bir hata oluştu.");
      } else {
        setTerminateError("Sunucuya ulaşılamadı. Lütfen tekrar deneyin.");
      }
    } finally {
      setIsTerminating(false);
    }
  };

  const formatCurrency = (val: number) => {
    return new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(val);
  };

  const formatDate = (dateStr: string) => {
    return new Date(dateStr).toLocaleString("tr-TR");
  };

  return (
    <div style={{ padding: "2rem", fontFamily: "Outfit, Inter, system-ui", color: "#f8fafc", backgroundColor: "#0f172a", minHeight: "100vh" }}>
      <header style={{ marginBottom: "2rem", borderBottom: "1px solid #334155", paddingBottom: "1rem" }}>
        <h1 style={{ color: "#38bdf8", fontSize: "2rem", fontWeight: 700, margin: 0 }}>Finans & Abonelik Yönetim Paneli</h1>
        <p style={{ color: "#94a3b8", marginTop: "0.5rem" }}>
          Ödeme iadelerini gerçekleştirebilir ve aktif abonelikleri kalıcı olarak sonlandırabilirsiniz.
        </p>
      </header>

      {errorMsg && (
        <div style={{ padding: "1rem", backgroundColor: "rgba(239, 68, 68, 0.15)", border: "1px solid #ef4444", borderRadius: "8px", color: "#fca5a5", marginBottom: "1.5rem" }}>
          {errorMsg}
        </div>
      )}

      {/* Results banner if operations succeeded */}
      {refundResult && (
        <div style={{ padding: "1.25rem", backgroundColor: "rgba(34, 197, 94, 0.15)", border: "1px solid #22c55e", borderRadius: "8px", color: "#86efac", marginBottom: "1.5rem", position: "relative" }}>
          <h4 style={{ margin: "0 0 0.5rem 0", color: "#4ade80" }}>İade Başarılı</h4>
          <p style={{ margin: 0, fontSize: "0.9rem" }}>
            Tutar: <strong>{formatCurrency(refundResult.amount)}</strong> | Kalan İade Edilebilir: <strong>{formatCurrency(refundResult.remaining)}</strong>
          </p>
          <button
            onClick={() => setRefundResult(null)}
            style={{ position: "absolute", top: "0.5rem", right: "0.5rem", background: "none", border: "none", color: "#86efac", cursor: "pointer", fontSize: "1.2rem" }}
          >
            &times;
          </button>
        </div>
      )}

      {terminateResult && (
        <div style={{ padding: "1.25rem", backgroundColor: "rgba(34, 197, 94, 0.15)", border: "1px solid #22c55e", borderRadius: "8px", color: "#86efac", marginBottom: "1.5rem", position: "relative" }}>
          <h4 style={{ margin: "0 0 0.5rem 0", color: "#4ade80" }}>Abonelik Sonlandırıldı</h4>
          <p style={{ margin: 0, fontSize: "0.9rem" }}>
            Abonelik ID: <strong>{terminateResult.subId}</strong> | Durum: <strong>{terminateResult.status}</strong> (Öğrencinin erişimi derhal iptal edilmiştir).
          </p>
          <button
            onClick={() => setTerminateResult(null)}
            style={{ position: "absolute", top: "0.5rem", right: "0.5rem", background: "none", border: "none", color: "#86efac", cursor: "pointer", fontSize: "1.2rem" }}
          >
            &times;
          </button>
        </div>
      )}

      {/* Subscriptions Section */}
      <section style={{ marginBottom: "3rem", backgroundColor: "#1e293b", borderRadius: "12px", border: "1px solid #334155", padding: "1.5rem", boxShadow: "0 4px 6px -1px rgba(0,0,0,0.2)" }}>
        <h2 style={{ color: "#f1f5f9", fontSize: "1.3rem", fontWeight: 600, margin: "0 0 1rem 0" }}>Abonelikler</h2>

        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.9rem" }}>
            <thead>
              <tr style={{ borderBottom: "2px solid #334155", color: "#94a3b8" }}>
                <th style={{ padding: "0.75rem" }}>ID</th>
                <th style={{ padding: "0.75rem" }}>Öğrenci</th>
                <th style={{ padding: "0.75rem" }}>Koç</th>
                <th style={{ padding: "0.75rem" }}>Paket</th>
                <th style={{ padding: "0.75rem" }}>Durum</th>
                <th style={{ padding: "0.75rem" }}>Başlangıç</th>
                <th style={{ padding: "0.75rem" }}>Bitiş</th>
                <th style={{ padding: "0.75rem" }}>Oto Yenileme</th>
                <th style={{ padding: "0.75rem" }}>Gerekçe</th>
                <th style={{ padding: "0.75rem", textAlign: "right" }}>Eylem</th>
              </tr>
            </thead>
            <tbody>
              {subscriptions.length === 0 ? (
                <tr>
                  <td colSpan={10} style={{ padding: "1.5rem", textAlign: "center", color: "#94a3b8" }}>
                    Abonelik kaydı bulunamadı.
                  </td>
                </tr>
              ) : (
                subscriptions.map((sub) => (
                  <tr key={sub.id} style={{ borderBottom: "1px solid #334155" }}>
                    <td style={{ padding: "0.75rem", fontWeight: 600 }}>{sub.id}</td>
                    <td style={{ padding: "0.75rem" }}>
                      <div>{sub.studentFullName}</div>
                      <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>{sub.studentEmail}</div>
                    </td>
                    <td style={{ padding: "0.75rem" }}>{sub.coachFullName}</td>
                    <td style={{ padding: "0.75rem" }}>{sub.packageName}</td>
                    <td style={{ padding: "0.75rem" }}>
                      <span style={{
                        padding: "0.25rem 0.5rem",
                        borderRadius: "9999px",
                        fontSize: "0.8rem",
                        fontWeight: 600,
                        backgroundColor: sub.status === "ACTIVE" ? "rgba(34, 197, 94, 0.2)" : sub.status === "TERMINATED" ? "rgba(239, 68, 68, 0.2)" : "rgba(100, 116, 139, 0.2)",
                        color: sub.status === "ACTIVE" ? "#4ade80" : sub.status === "TERMINATED" ? "#fca5a5" : "#cbd5e1"
                      }}>
                        {sub.status}
                      </span>
                    </td>
                    <td style={{ padding: "0.75rem" }}>{formatDate(sub.startAt)}</td>
                    <td style={{ padding: "0.75rem" }}>{formatDate(sub.endAt)}</td>
                    <td style={{ padding: "0.75rem" }}>{sub.autoRenew ? "Evet" : "Hayır"}</td>
                    <td style={{ padding: "0.75rem", color: "#94a3b8", fontSize: "0.8rem" }}>{sub.terminationReason || "-"}</td>
                    <td style={{ padding: "0.75rem", textAlign: "right" }}>
                      {sub.status !== "TERMINATED" ? (
                        <button
                          onClick={() => {
                            setTerminateSub(sub);
                            setTerminateError(null);
                          }}
                          style={{ padding: "0.4rem 0.8rem", backgroundColor: "#ef4444", color: "#white", border: "none", borderRadius: "6px", cursor: "pointer", fontWeight: 600 }}
                        >
                          Sonlandır
                        </button>
                      ) : (
                        <span style={{ color: "#64748b", fontSize: "0.85rem" }}>Sonlandırıldı</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Subscriptions Pagination */}
        {subTotal > pageSize && (
          <div style={{ marginTop: "1rem", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: "0.85rem", color: "#94a3b8" }}>
              Toplam {subTotal} kayıttan {subPage * pageSize + 1} - {Math.min((subPage + 1) * pageSize, subTotal)} arası gösteriliyor
            </span>
            <div style={{ display: "flex", gap: "0.5rem" }}>
              <button
                onClick={() => setSubPage(p => Math.max(0, p - 1))}
                disabled={subPage === 0 || isLoading}
                style={{ padding: "0.4rem 0.8rem", backgroundColor: "#334155", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer" }}
              >
                Geri
              </button>
              <button
                onClick={() => setSubPage(p => p + 1)}
                disabled={(subPage + 1) * pageSize >= subTotal || isLoading}
                style={{ padding: "0.4rem 0.8rem", backgroundColor: "#334155", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer" }}
              >
                İleri
              </button>
            </div>
          </div>
        )}
      </section>

      {/* Payments Section */}
      <section style={{ backgroundColor: "#1e293b", borderRadius: "12px", border: "1px solid #334155", padding: "1.5rem", boxShadow: "0 4px 6px -1px rgba(0,0,0,0.2)" }}>
        <h2 style={{ color: "#f1f5f9", fontSize: "1.3rem", fontWeight: 600, margin: "0 0 1rem 0" }}>Ödemeler & İşlemler</h2>

        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", textAlign: "left", fontSize: "0.9rem" }}>
            <thead>
              <tr style={{ borderBottom: "2px solid #334155", color: "#94a3b8" }}>
                <th style={{ padding: "0.75rem" }}>ID</th>
                <th style={{ padding: "0.75rem" }}>Abonelik ID</th>
                <th style={{ padding: "0.75rem" }}>Öğrenci</th>
                <th style={{ padding: "0.75rem" }}>Koç</th>
                <th style={{ padding: "0.75rem" }}>Paket</th>
                <th style={{ padding: "0.75rem" }}>Tür</th>
                <th style={{ padding: "0.75rem" }}>Tutar</th>
                <th style={{ padding: "0.75rem" }}>İade Edilen</th>
                <th style={{ padding: "0.75rem" }}>Kalan Tutar</th>
                <th style={{ padding: "0.75rem" }}>Durum</th>
                <th style={{ padding: "0.75rem" }}>Oluşturulma</th>
                <th style={{ padding: "0.75rem", textAlign: "right" }}>Eylem</th>
              </tr>
            </thead>
            <tbody>
              {payments.length === 0 ? (
                <tr>
                  <td colSpan={12} style={{ padding: "1.5rem", textAlign: "center", color: "#94a3b8" }}>
                    Ödeme kaydı bulunamadı.
                  </td>
                </tr>
              ) : (
                payments.map((p) => (
                  <tr key={p.id} style={{ borderBottom: "1px solid #334155" }}>
                    <td style={{ padding: "0.75rem", fontWeight: 600 }}>{p.id}</td>
                    <td style={{ padding: "0.75rem" }}>{p.subscriptionId}</td>
                    <td style={{ padding: "0.75rem" }}>
                      <div>{p.studentFullName}</div>
                      <div style={{ fontSize: "0.75rem", color: "#94a3b8" }}>{p.studentEmail}</div>
                    </td>
                    <td style={{ padding: "0.75rem" }}>{p.coachFullName}</td>
                    <td style={{ padding: "0.75rem" }}>{p.packageName}</td>
                    <td style={{ padding: "0.75rem" }}>
                      <span style={{
                        fontSize: "0.85rem",
                        fontWeight: 600,
                        color: p.type === "CHARGE" ? "#38bdf8" : "#fb7185"
                      }}>
                        {p.type === "CHARGE" ? "ÖDEME" : "İADE"}
                      </span>
                    </td>
                    <td style={{ padding: "0.75rem", fontWeight: 600 }}>{formatCurrency(p.amount)}</td>
                    <td style={{ padding: "0.75rem", color: "#fb7185" }}>{p.type === "CHARGE" ? formatCurrency(p.refundedAmount) : "-"}</td>
                    <td style={{ padding: "0.75rem", color: "#34d399" }}>{p.type === "CHARGE" ? formatCurrency(p.remainingRefundableAmount) : "-"}</td>
                    <td style={{ padding: "0.75rem" }}>
                      <span style={{
                        padding: "0.25rem 0.5rem",
                        borderRadius: "9999px",
                        fontSize: "0.8rem",
                        fontWeight: 600,
                        backgroundColor: p.status === "SUCCESS" ? "rgba(34, 197, 94, 0.2)" : "rgba(239, 68, 68, 0.2)",
                        color: p.status === "SUCCESS" ? "#4ade80" : "#fca5a5"
                      }}>
                        {p.status}
                      </span>
                    </td>
                    <td style={{ padding: "0.75rem" }}>{formatDate(p.createdAt)}</td>
                    <td style={{ padding: "0.75rem", textAlign: "right" }}>
                      {p.type === "CHARGE" && p.status === "SUCCESS" && p.remainingRefundableAmount > 0 ? (
                        <button
                          onClick={() => {
                            setRefundPayment(p);
                            setRefundAmount(p.remainingRefundableAmount.toString());
                            setRefundError(null);
                          }}
                          style={{ padding: "0.4rem 0.8rem", backgroundColor: "#0284c7", color: "white", border: "none", borderRadius: "6px", cursor: "pointer", fontWeight: 600 }}
                        >
                          İade Et
                        </button>
                      ) : (
                        <span style={{ color: "#64748b", fontSize: "0.85rem" }}>İade Edilemez</span>
                      )}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Payments Pagination */}
        {paymentTotal > pageSize && (
          <div style={{ marginTop: "1rem", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={{ fontSize: "0.85rem", color: "#94a3b8" }}>
              Toplam {paymentTotal} kayıttan {paymentPage * pageSize + 1} - {Math.min((paymentPage + 1) * pageSize, paymentTotal)} arası gösteriliyor
            </span>
            <div style={{ display: "flex", gap: "0.5rem" }}>
              <button
                onClick={() => setPaymentPage(p => Math.max(0, p - 1))}
                disabled={paymentPage === 0 || isLoading}
                style={{ padding: "0.4rem 0.8rem", backgroundColor: "#334155", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer" }}
              >
                Geri
              </button>
              <button
                onClick={() => setPaymentPage(p => p + 1)}
                disabled={(paymentPage + 1) * pageSize >= paymentTotal || isLoading}
                style={{ padding: "0.4rem 0.8rem", backgroundColor: "#334155", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer" }}
              >
                İleri
              </button>
            </div>
          </div>
        )}
      </section>

      {/* Refund Modal */}
      {refundPayment && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="refund-modal-title"
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: "rgba(15, 23, 42, 0.8)",
            backdropFilter: "blur(4px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
            padding: "1rem"
          }}
        >
          <div style={{
            backgroundColor: "#1e293b",
            border: "1px solid #334155",
            borderRadius: "12px",
            width: "100%",
            maxWidth: "500px",
            padding: "2rem",
            boxShadow: "0 25px 50px -12px rgba(0,0,0,0.5)"
          }}>
            <h3 id="refund-modal-title" style={{ margin: "0 0 1rem 0", color: "#38bdf8", fontSize: "1.4rem", fontWeight: 700 }}>
              Ödeme İadesi Gerçekleştir
            </h3>

            {refundError && (
              <div style={{ padding: "0.75rem", backgroundColor: "rgba(239, 68, 68, 0.15)", border: "1px solid #ef4444", borderRadius: "6px", color: "#fca5a5", marginBottom: "1rem", fontSize: "0.85rem" }}>
                {refundError}
              </div>
            )}

            <div style={{ marginBottom: "1rem", fontSize: "0.9rem", color: "#cbd5e1" }}>
              <div style={{ marginBottom: "0.4rem" }}>Ödeme ID: <strong>{refundPayment.id}</strong></div>
              <div style={{ marginBottom: "0.4rem" }}>Orijinal Tutar: <strong>{formatCurrency(refundPayment.amount)}</strong></div>
              <div style={{ marginBottom: "0.4rem" }}>Daha Önce İade Edilen: <strong style={{ color: "#fb7185" }}>{formatCurrency(refundPayment.refundedAmount)}</strong></div>
              <div>Maksimum İade Edilebilir: <strong style={{ color: "#34d399" }}>{formatCurrency(refundPayment.remainingRefundableAmount)}</strong></div>
            </div>

            <form onSubmit={handleRefundSubmit}>
              <div style={{ marginBottom: "1rem" }}>
                <label style={{ display: "block", marginBottom: "0.4rem", fontSize: "0.85rem", color: "#94a3b8" }}>İade Tutarı (TRY)</label>
                <input
                  type="text"
                  value={refundAmount}
                  onChange={(e) => setRefundAmount(e.target.value)}
                  disabled={isRefunding}
                  style={{
                    width: "100%",
                    padding: "0.75rem",
                    backgroundColor: "#0f172a",
                    border: "1px solid #334155",
                    borderRadius: "6px",
                    color: "#f8fafc",
                    fontSize: "1rem"
                  }}
                  required
                />
              </div>

              <div style={{ marginBottom: "1.5rem" }}>
                <label style={{ display: "block", marginBottom: "0.4rem", fontSize: "0.85rem", color: "#94a3b8" }}>İade Gerekçesi</label>
                <textarea
                  value={refundReason}
                  onChange={(e) => setRefundReason(e.target.value)}
                  disabled={isRefunding}
                  style={{
                    width: "100%",
                    height: "80px",
                    padding: "0.75rem",
                    backgroundColor: "#0f172a",
                    border: "1px solid #334155",
                    borderRadius: "6px",
                    color: "#f8fafc",
                    fontSize: "0.9rem",
                    resize: "none"
                  }}
                  required
                />
              </div>

              <div style={{
                padding: "0.75rem",
                backgroundColor: "rgba(245, 158, 11, 0.1)",
                border: "1px solid #f59e0b",
                borderRadius: "6px",
                color: "#fcd34d",
                fontSize: "0.8rem",
                marginBottom: "1.5rem"
              }}>
                <strong>UYARI:</strong> Bu işlem, ödeme sağlayıcısına (Iyzico) doğrudan bir iade isteği gönderir ve geri alınamaz.
              </div>

              <div style={{ display: "flex", gap: "1rem", justifyContent: "flex-end" }}>
                <button
                  type="button"
                  onClick={() => setRefundPayment(null)}
                  disabled={isRefunding}
                  style={{ padding: "0.6rem 1.2rem", backgroundColor: "#334155", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer", fontWeight: 600 }}
                >
                  Vazgeç
                </button>
                <button
                  type="submit"
                  disabled={isRefunding}
                  style={{ padding: "0.6rem 1.2rem", backgroundColor: "#0284c7", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer", fontWeight: 600 }}
                >
                  {isRefunding ? "Gönderiliyor..." : "İadeyi Başlat"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Termination Modal */}
      {terminateSub && (
        <div
          role="dialog"
          aria-modal="true"
          aria-labelledby="terminate-modal-title"
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: "rgba(15, 23, 42, 0.8)",
            backdropFilter: "blur(4px)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
            padding: "1rem"
          }}
        >
          <div style={{
            backgroundColor: "#1e293b",
            border: "1px solid #334155",
            borderRadius: "12px",
            width: "100%",
            maxWidth: "500px",
            padding: "2rem",
            boxShadow: "0 25px 50px -12px rgba(0,0,0,0.5)"
          }}>
            <h3 id="terminate-modal-title" style={{ margin: "0 0 1rem 0", color: "#f87171", fontSize: "1.4rem", fontWeight: 700 }}>
              Aboneliği Kalıcı Olarak Sonlandır
            </h3>

            {terminateError && (
              <div style={{ padding: "0.75rem", backgroundColor: "rgba(239, 68, 68, 0.15)", border: "1px solid #ef4444", borderRadius: "6px", color: "#fca5a5", marginBottom: "1rem", fontSize: "0.85rem" }}>
                {terminateError}
              </div>
            )}

            <div style={{ marginBottom: "1.25rem", fontSize: "0.9rem", color: "#cbd5e1" }}>
              <div style={{ marginBottom: "0.4rem" }}>Abonelik ID: <strong>{terminateSub.id}</strong></div>
              <div style={{ marginBottom: "0.4rem" }}>Öğrenci: <strong>{terminateSub.studentFullName}</strong></div>
              <div style={{ marginBottom: "0.4rem" }}>Koç: <strong>{terminateSub.coachFullName}</strong></div>
              <div>Mevcut Bitiş Tarihi: <strong>{formatDate(terminateSub.endAt)}</strong></div>
            </div>

            <form onSubmit={handleTerminateSubmit}>
              <div style={{ marginBottom: "1.5rem" }}>
                <label style={{ display: "block", marginBottom: "0.4rem", fontSize: "0.85rem", color: "#94a3b8" }}>Sonlandırma Gerekçesi</label>
                <textarea
                  value={terminateReason}
                  onChange={(e) => setTerminateReason(e.target.value)}
                  disabled={isTerminating}
                  placeholder="Lütfen bu aboneliği sonlandırma nedeninizi detaylıca belirtin."
                  style={{
                    width: "100%",
                    height: "90px",
                    padding: "0.75rem",
                    backgroundColor: "#0f172a",
                    border: "1px solid #334155",
                    borderRadius: "6px",
                    color: "#f8fafc",
                    fontSize: "0.9rem",
                    resize: "none"
                  }}
                  required
                />
              </div>

              <div style={{
                padding: "0.85rem",
                backgroundColor: "rgba(239, 68, 68, 0.1)",
                border: "1px solid #ef4444",
                borderRadius: "6px",
                color: "#fca5a5",
                fontSize: "0.85rem",
                marginBottom: "1.5rem",
                lineHeight: "1.4"
              }}>
                <strong>ÖNEMLİ UYARI:</strong> Aboneliği sonlandırmak öğrencinin erişimini <strong>hemen</strong> kaldırır.
                Bu işlem öğrencinin yapacağı yenileme iptalinden (autoRenew=false) farklıdır, çünkü erişim hakları
                bitiş tarihine kadar korunmaz, derhal sonlandırılır.
              </div>

              <div style={{ display: "flex", gap: "1rem", justifyContent: "flex-end" }}>
                <button
                  type="button"
                  onClick={() => setTerminateSub(null)}
                  disabled={isTerminating}
                  style={{ padding: "0.6rem 1.2rem", backgroundColor: "#334155", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer", fontWeight: 600 }}
                >
                  Vazgeç
                </button>
                <button
                  type="submit"
                  disabled={isTerminating}
                  style={{ padding: "0.6rem 1.2rem", backgroundColor: "#ef4444", color: "#fff", border: "none", borderRadius: "6px", cursor: "pointer", fontWeight: 600 }}
                >
                  {isTerminating ? "İşleniyor..." : "Aboneliği Sonlandır"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
