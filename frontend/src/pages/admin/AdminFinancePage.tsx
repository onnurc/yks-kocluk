import { useEffect, useState } from "react";
import { financeApi } from "../../safety/financeApi";
import type {
  AdminFinanceSummary,
  AdminPaymentResponse,
  AdminSubscriptionResponse,
  AdminRefundAuditResponse,
} from "../../safety/financeTypes";
import "./admin.css";

const money = new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" });

export const AdminFinancePage = () => {
  const [summary, setSummary] = useState<AdminFinanceSummary | null>(null);
  const [payments, setPayments] = useState<AdminPaymentResponse[]>([]);
  const [subscriptions, setSubscriptions] = useState<AdminSubscriptionResponse[]>([]);
  const [refundAudit, setRefundAudit] = useState<AdminRefundAuditResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [refund, setRefund] = useState<AdminPaymentResponse | null>(null);
  const [terminate, setTerminate] = useState<AdminSubscriptionResponse | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    Promise.all([financeApi.summary(), financeApi.listPayments(0, 20), financeApi.listSubscriptions(0, 20), financeApi.listRefundAudit(0, 20)])
      .then(([finance, paymentPage, subscriptionPage, refundPage]) => {
        if (!active) return;
        setSummary(finance);
        setPayments(paymentPage.content ?? []);
        setSubscriptions(subscriptionPage.content ?? []);
        setRefundAudit(refundPage.content ?? []);
      })
      .catch(() => active && setError(true))
      .finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [version]);

  const refresh = () => {
    setLoading(true);
    setError(false);
    setVersion((value) => value + 1);
  };

  const doRefund = async () => {
    if (!refund || busy) return;
    setBusy(true);
    try {
      await financeApi.refund(refund.id, refund.remainingRefundableAmount, "Admin paneli - uygun iade işlemi");
      setNotice("İade işlemi ödeme sağlayıcısında başarıyla tamamlandı.");
      setRefund(null);
      refresh();
    } catch {
      setNotice("İade tamamlanamadı. Uygunluk ve ödeme durumunu yeniden kontrol edin.");
    } finally {
      setBusy(false);
    }
  };

  const doTerminate = async () => {
    if (!terminate || !reason.trim() || busy) return;
    setBusy(true);
    try {
      await financeApi.terminateSubscription(terminate.id, reason.trim());
      setNotice("Abonelik sonlandırıldı.");
      setTerminate(null);
      setReason("");
      refresh();
    } catch {
      setNotice("Abonelik sonlandırılamadı.");
    } finally {
      setBusy(false);
    }
  };

  return <main className="admin-page">
    <header className="admin-page__heading"><div><p className="admin-page__eyebrow">Abonelik & finans</p><h1>Finans</h1><p>Gerçek tahsilat, iade ve abonelik kayıtlarını ürün seviyesinde yönetin.</p></div></header>
    {notice && <p className={`admin-feedback ${notice.includes("tamamlanamadı") || notice.includes("sonlandırılamadı") ? "is-error" : ""}`}>{notice}</p>}
    {error ? <div className="admin-error" role="alert">Finans verileri alınamadı. <button className="admin-button admin-button--secondary" onClick={refresh}>Yeniden dene</button></div> : <>
      <section className="admin-metrics" aria-label="Finans özeti">
        <article className="admin-metric admin-metric--navy"><span>Brüt Tahsilat</span><strong>{money.format(summary?.grossRevenue ?? 0)}</strong></article>
        <article className="admin-metric"><span>İade</span><strong>{money.format(summary?.refundTotal ?? 0)}</strong></article>
        <article className="admin-metric admin-metric--gold"><span>Net Tahsilat</span><strong>{money.format(summary?.netCollectedAmount ?? 0)}</strong></article>
        <article className="admin-metric"><span>Başarılı Ödeme</span><strong>{summary?.successfulPaymentCount ?? 0}</strong></article>
      </section>

      <section className="admin-panel admin-section">
        <div className="admin-panel__heading"><h2>Abonelikler</h2></div>
        {loading ? <div className="admin-loading">Abonelikler yükleniyor…</div> : subscriptions.length === 0 ? <div className="admin-empty">Abonelik kaydı bulunmuyor.</div> :
          <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Öğrenci</th><th>Koç</th><th>Paket</th><th>Dönem</th><th>Durum</th><th></th></tr></thead><tbody>{subscriptions.map((subscription) => <tr key={subscription.id}>
            <td><strong>{subscription.studentFullName}</strong><small>{subscription.studentEmail}</small></td><td>{subscription.coachFullName}</td><td>{subscription.packageName}</td>
            <td>{new Date(subscription.startAt).toLocaleDateString("tr-TR")} – {new Date(subscription.endAt).toLocaleDateString("tr-TR")}</td>
            <td><span className="admin-badge">{subscription.status}</span></td>
            <td>{!["TERMINATED", "EXPIRED", "CANCELLED"].includes(subscription.status) && <button className="admin-button admin-button--danger" onClick={() => setTerminate(subscription)}>Sonlandır</button>}</td>
          </tr>)}</tbody></table></div>}
      </section>

      <section className="admin-panel">
        <div className="admin-panel__heading"><h2>Ödeme İşlemleri</h2></div>
        {loading ? <div className="admin-loading">Ödemeler yükleniyor…</div> : payments.length === 0 ? <div className="admin-empty">Ödeme işlemi bulunmuyor.</div> :
          <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Öğrenci / Koç</th><th>Paket</th><th>Tahsilat</th><th>İade</th><th>Kalan</th><th>Tarih</th><th>İade durumu</th></tr></thead><tbody>{payments.map((payment) => <tr key={payment.id}>
            <td><strong>{payment.studentFullName}</strong><small>{payment.coachFullName}</small></td><td>{payment.packageName}</td><td>{money.format(payment.amount)}</td><td>{money.format(payment.refundedAmount)}</td><td>{money.format(payment.remainingRefundableAmount)}</td><td>{new Date(payment.succeededAt ?? payment.createdAt).toLocaleDateString("tr-TR")}</td>
            <td>{payment.type === "CHARGE" && payment.status === "SUCCESS" ? payment.refundEligible ?
              <button className="admin-button" onClick={() => setRefund(payment)}>İade Et</button> :
              <span className="admin-refund-state"><button className="admin-button admin-button--secondary" disabled>İade Et</button><small>{payment.refundIneligibleReason ?? "İade edilemez"}</small></span> :
              <span className="admin-badge">{payment.type} · {payment.status}</span>}</td>
          </tr>)}</tbody></table></div>}
      </section>

      <section className="admin-panel admin-section">
        <div className="admin-panel__heading"><h2>Öğrenci İade Geçmişi</h2><p>Öğrenciler tarafından paket politikasına göre sonuçlandırılan iadeler salt okunur olarak gösterilir.</p></div>
        {loading ? <div className="admin-loading">İade geçmişi yükleniyor…</div> : refundAudit.length === 0 ? <div className="admin-empty">İade kaydı bulunmuyor.</div> :
          <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Öğrenci / Koç</th><th>Paket</th><th>Tutar</th><th>Durum</th><th>Ödeme / Abonelik</th><th>Tarih</th></tr></thead><tbody>{refundAudit.map((item) => <tr key={item.id}>
            <td><strong>{item.studentName}</strong><small>{item.coachName}</small></td><td>{item.packageName}</td><td>{money.format(item.refundedAmount || item.amount)}</td><td><span className="admin-badge">{item.status}</span></td><td><small>Ödeme #{item.originalPaymentId}{item.refundPaymentId ? ` → #${item.refundPaymentId}` : ""}</small><small>Abonelik #{item.subscriptionId}</small></td><td>{new Date(item.requestedAt).toLocaleDateString("tr-TR")}</td>
          </tr>)}</tbody></table></div>}
      </section>
    </>}

    {refund && <div className="admin-modal-backdrop"><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="refund-title"><h2 id="refund-title">İadeyi doğrula</h2><p><strong>{refund.studentFullName}</strong> için {money.format(refund.remainingRefundableAmount)} tutarındaki kalan tahsilat gerçek ödeme sağlayıcısı üzerinden iade edilecek.</p><p>Backend uygunluğu ve kalan tutarı işlem anında yeniden doğrulayacaktır.</p><div className="admin-actions"><button className="admin-button admin-button--secondary" onClick={() => setRefund(null)}>Vazgeç</button><button className="admin-button admin-button--danger" disabled={busy} onClick={() => void doRefund()}>{busy ? "İşleniyor…" : "İadeyi Gerçekleştir"}</button></div></section></div>}
    {terminate && <div className="admin-modal-backdrop"><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="terminate-title"><h2 id="terminate-title">Aboneliği sonlandır</h2><div className="admin-form"><label>Operasyon gerekçesi<textarea rows={4} value={reason} onChange={(event) => setReason(event.target.value)} required /></label><div className="admin-actions"><button className="admin-button admin-button--secondary" onClick={() => setTerminate(null)}>Vazgeç</button><button className="admin-button admin-button--danger" disabled={busy || !reason.trim()} onClick={() => void doTerminate()}>Sonlandırmayı Onayla</button></div></div></section></div>}
  </main>;
};
