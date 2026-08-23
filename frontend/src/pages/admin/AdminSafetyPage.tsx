import { useEffect, useState } from "react";
import { safetyApi } from "../../safety/safetyApi";
import type { ReportResponse, ReportStatus } from "../../safety/safetyTypes";
import "./admin.css";

const statusLabels: Record<ReportStatus, string> = {
  OPEN: "Açık",
  REVIEWED: "İnceleniyor",
  RESOLVED: "Çözüldü",
  DISMISSED: "Reddedildi",
};

const targetLabels: Record<ReportResponse["targetType"], string> = {
  USER: "Kullanıcı",
  CONVERSATION: "Konuşma",
  MESSAGE: "Mesaj",
};

export const AdminSafetyPage = () => {
  const [reports, setReports] = useState<ReportResponse[]>([]);
  const [status, setStatus] = useState<"ALL" | ReportStatus>("ALL");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [selected, setSelected] = useState<ReportResponse | null>(null);
  const [nextStatus, setNextStatus] = useState<ReportStatus>("REVIEWED");
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    safetyApi.listReports(status === "ALL" ? undefined : status, page, 20).then((response) => {
      if (active) { setReports(response.content ?? []); setTotalPages(Math.max(response.totalPages ?? 1, 1)); }
    }).catch(() => active && setError(true)).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [page, status, version]);

  const openStatus = (report: ReportResponse) => {
    setSelected(report);
    setNextStatus(report.status === "OPEN" ? "REVIEWED" : "RESOLVED");
    setNotice("");
  };

  const updateStatus = async () => {
    if (!selected || busy || selected.status === nextStatus) return;
    setBusy(true);
    try {
      const updated = await safetyApi.updateReportStatus(selected.id, nextStatus);
      setReports((current) => current.map((report) => report.id === updated.id ? updated : report));
      setSelected(null);
      setNotice("Rapor durumu güncellendi.");
    } catch {
      setNotice("Rapor durumu güncellenemedi. Geçişi ve güncel durumu kontrol edin.");
    } finally {
      setBusy(false);
    }
  };

  return <main className="admin-page">
    <header className="admin-page__heading"><div><p className="admin-page__eyebrow">Güvenlik ve destek</p><h1>Raporlar</h1><p>Kullanıcı şikâyetlerini gerçek durumlarıyla inceleyin ve sonuçlandırın.</p></div></header>
    {notice && <p className={`admin-feedback ${notice.includes("güncellenemedi") ? "is-error" : ""}`}>{notice}</p>}
    <section className="admin-panel">
      <div className="admin-toolbar">
        <label className="admin-filter-label">Durum
          <select aria-label="Rapor durumu" value={status} onChange={(event) => { setLoading(true); setError(false); setStatus(event.target.value as "ALL" | ReportStatus); setPage(0); }}>
            <option value="ALL">Tüm durumlar</option><option value="OPEN">Açık</option><option value="REVIEWED">İnceleniyor</option><option value="RESOLVED">Çözüldü</option><option value="DISMISSED">Reddedildi</option>
          </select>
        </label>
      </div>
      {error ? <div className="admin-error" role="alert">Raporlar şu anda alınamadı. <button className="admin-button admin-button--secondary" onClick={() => { setLoading(true); setError(false); setVersion((value) => value + 1); }}>Yeniden dene</button></div>
        : loading ? <div className="admin-loading">Raporlar yükleniyor…</div>
        : reports.length === 0 ? <div className="admin-empty">Bu durumda rapor bulunmuyor.</div>
        : <div className="admin-card-list">{reports.map((report) => <article className="admin-card" key={report.id}>
          <div className="admin-card__top"><div><h3>{report.reason}</h3><p>{targetLabels[report.targetType]} #{report.targetId} · Bildiren kullanıcı #{report.reporterUserId}</p></div><span className={`admin-badge ${report.status === "OPEN" ? "admin-badge--danger" : report.status === "REVIEWED" ? "admin-badge--warning" : "admin-badge--success"}`}>{statusLabels[report.status]}</span></div>
          {report.details && <p>{report.details}</p>}
          <small>{new Date(report.createdAt).toLocaleString("tr-TR")}</small>
          {(report.status === "OPEN" || report.status === "REVIEWED") && <div className="admin-actions"><button className="admin-button admin-button--secondary" onClick={() => openStatus(report)}>Durumu yönet</button></div>}
        </article>)}</div>}
      {totalPages > 1 && <div className="admin-pagination"><button className="admin-button admin-button--secondary" disabled={page === 0} onClick={() => { setLoading(true); setPage((value) => value - 1); }}>Önceki</button><span>{page + 1} / {totalPages}</span><button className="admin-button admin-button--secondary" disabled={page >= totalPages - 1} onClick={() => { setLoading(true); setPage((value) => value + 1); }}>Sonraki</button></div>}
    </section>
    {selected && <div className="admin-modal-backdrop"><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="report-status-title"><h2 id="report-status-title">Rapor durumunu güncelle</h2><p><strong>{selected.reason}</strong> raporu için gerçek backend durum geçişi uygulanacaktır.</p><div className="admin-form"><label>Yeni durum<select value={nextStatus} onChange={(event) => setNextStatus(event.target.value as ReportStatus)}>{selected.status === "OPEN" && <option value="REVIEWED">İnceleniyor</option>}<option value="RESOLVED">Çözüldü</option><option value="DISMISSED">Reddedildi</option></select></label></div><div className="admin-actions"><button className="admin-button admin-button--secondary" onClick={() => setSelected(null)}>Vazgeç</button><button className="admin-button" disabled={busy || selected.status === nextStatus} onClick={() => void updateStatus()}>{busy ? "Güncelleniyor…" : "Durumu Güncelle"}</button></div></section></div>}
  </main>;
};
