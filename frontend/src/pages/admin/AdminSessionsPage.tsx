import { useEffect, useState } from "react";
import { adminApi } from "../../admin/adminApi";
import type { AdminSession } from "../../admin/adminTypes";
import { ApiError } from "../../api/ApiError";
import "./admin.css";

const startOfDay = (value: string) => value ? new Date(`${value}T00:00:00`).toISOString() : undefined;
const endOfDay = (value: string) => value ? new Date(`${value}T23:59:59.999`).toISOString() : undefined;

export const AdminSessionsPage = () => {
  const [items, setItems] = useState<AdminSession[]>([]);
  const [type, setType] = useState("ALL");
  const [status, setStatus] = useState("");
  const [coachId, setCoachId] = useState("");
  const [studentId, setStudentId] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [filters, setFilters] = useState({ type: "ALL", status: "", coachId: "", studentId: "", from: "", to: "" });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [confirming, setConfirming] = useState<AdminSession | null>(null);
  const [meetingUrl, setMeetingUrl] = useState("");
  const [confirmError, setConfirmError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const confirmTrial = async () => {
    if (!confirming || busy) return;
    setBusy(true); setConfirmError(null);
    try {
      await adminApi.confirmTrial(confirming.id, meetingUrl.trim());
      setItems((current) => current.map((item) => item.type === "TRIAL" && item.id === confirming.id
        ? { ...item, status: "CONFIRMED", meetingUrl: meetingUrl.trim() } : item));
      setConfirming(null); setMeetingUrl("");
    } catch (caught) {
      setConfirmError(caught instanceof ApiError ? caught.detail : "Görüşme onaylanamadı.");
    } finally { setBusy(false); }
  };

  useEffect(() => {
    let active = true;
    adminApi.sessions({
      type: filters.type,
      status: filters.status,
      coachId: filters.coachId ? Number(filters.coachId) : undefined,
      studentId: filters.studentId ? Number(filters.studentId) : undefined,
      from: startOfDay(filters.from),
      to: endOfDay(filters.to),
    }).then((response) => active && setItems(response.content ?? []))
      .catch(() => active && setError(true)).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [filters]);

  return <main className="admin-page">
    <header className="admin-page__heading"><div><p className="admin-page__eyebrow">Takvim ve operasyon</p><h1>Seanslar</h1><p>Ücretli seansları ve deneme görüşmelerini tek operasyon görünümünde inceleyin.</p></div></header>
    <section className="admin-panel">
      <form className="admin-toolbar" onSubmit={(event) => { event.preventDefault(); setLoading(true); setError(false); setFilters({ type, status, coachId, studentId, from, to }); }}>
        <select aria-label="Görüşme türü" value={type} onChange={(event) => setType(event.target.value)}><option value="ALL">Tüm görüşmeler</option><option value="PAID">Ücretli seans</option><option value="TRIAL">Deneme görüşmesi</option></select>
        <select aria-label="Seans durumu" value={status} onChange={(event) => setStatus(event.target.value)}><option value="">Tüm durumlar</option><option value="PLANNED">Planlandı</option><option value="COMPLETED">Tamamlandı</option><option value="CANCELLED">İptal</option><option value="LATE_CANCELLED">Geç iptal</option><option value="NO_SHOW">Katılmadı</option><option value="REQUESTED">Talep edildi</option><option value="CONFIRMED">Onaylandı</option></select>
        <input aria-label="Koç profil ID" type="number" min="1" placeholder="Koç profil ID" value={coachId} onChange={(event) => setCoachId(event.target.value)} />
        <input aria-label="Öğrenci ID" type="number" min="1" placeholder="Öğrenci ID" value={studentId} onChange={(event) => setStudentId(event.target.value)} />
        <label className="admin-filter-label">Başlangıç<input aria-label="Başlangıç tarihi" type="date" value={from} onChange={(event) => setFrom(event.target.value)} /></label>
        <label className="admin-filter-label">Bitiş<input aria-label="Bitiş tarihi" type="date" value={to} min={from || undefined} onChange={(event) => setTo(event.target.value)} /></label>
        <button className="admin-button">Filtrele</button>
      </form>
      {error ? <div className="admin-error" role="alert">Seanslar alınamadı. <button className="admin-button admin-button--secondary" onClick={() => { setLoading(true); setError(false); setFilters({ type, status, coachId, studentId, from, to }); }}>Yeniden dene</button></div>
        : loading ? <div className="admin-loading">Seanslar yükleniyor…</div>
        : items.length === 0 ? <div className="admin-empty">Bu filtrelerle eşleşen seans veya deneme görüşmesi yok.</div>
        : <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Tür</th><th>Öğrenci</th><th>Koç</th><th>Tarih</th><th>Durum</th><th>Bağlantı</th><th></th></tr></thead><tbody>{items.map((session) => <tr key={`${session.type}-${session.id}`}><td><span className="admin-badge admin-badge--navy">{session.type === "TRIAL" ? "Deneme" : "Ücretli"}</span></td><td>{session.studentName}{session.type === "TRIAL" && session.studentEmail && <small>{session.studentEmail}</small>}</td><td>{session.coachName}</td><td>{new Date(session.startsAt).toLocaleString("tr-TR")}</td><td><span className="admin-badge">{session.status}</span></td><td>{session.meetingUrl ? <a href={session.meetingUrl} target="_blank" rel="noreferrer">Hazır</a> : "Bekliyor"}</td><td>{session.type === "TRIAL" && session.status === "REQUESTED" && <button className="admin-button" type="button" onClick={() => { setConfirming(session); setMeetingUrl(""); setConfirmError(null); }}>Bağlantı Ekle ve Onayla</button>}</td></tr>)}</tbody></table></div>}
    </section>
    {confirming && <div className="admin-modal-backdrop"><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="trial-confirm-title"><h2 id="trial-confirm-title">Deneme görüşmesini onayla</h2><p><strong>{confirming.studentName}</strong> ve <strong>{confirming.coachName}</strong> için {new Date(confirming.startsAt).toLocaleString("tr-TR")} tarihli görüşme.</p><div className="admin-form"><label>HTTPS görüşme bağlantısı<input type="url" required placeholder="https://meet.google.com/..." value={meetingUrl} onChange={(event)=>setMeetingUrl(event.target.value)} /></label>{confirmError&&<p className="admin-feedback is-error" role="alert">{confirmError}</p>}<div className="admin-actions"><button className="admin-button admin-button--secondary" type="button" disabled={busy} onClick={()=>setConfirming(null)}>Vazgeç</button><button className="admin-button" type="button" disabled={busy||!meetingUrl.trim().startsWith("https://")} onClick={()=>void confirmTrial()}>{busy?"Onaylanıyor…":"Onayla ve Bildir"}</button></div></div></section></div>}
  </main>;
};
