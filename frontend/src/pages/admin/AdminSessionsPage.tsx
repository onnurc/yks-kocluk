import { useEffect, useState } from "react";
import { adminApi } from "../../admin/adminApi";
import type { AdminSession } from "../../admin/adminTypes";
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
        : <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Tür</th><th>Öğrenci</th><th>Koç</th><th>Tarih</th><th>Durum</th></tr></thead><tbody>{items.map((session) => <tr key={`${session.type}-${session.id}`}><td><span className="admin-badge admin-badge--navy">{session.type === "TRIAL" ? "Deneme" : "Ücretli"}</span></td><td>{session.studentName}</td><td>{session.coachName}</td><td>{new Date(session.startsAt).toLocaleString("tr-TR")}</td><td><span className="admin-badge">{session.status}</span></td></tr>)}</tbody></table></div>}
    </section>
  </main>;
};
