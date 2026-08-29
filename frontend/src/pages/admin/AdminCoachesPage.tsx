import { useEffect, useState } from "react";
import { adminApi } from "../../admin/adminApi";
import type { AdminCoach, CoachFilter, CoachStudent } from "../../admin/adminTypes";
import { AdminProfilePhotoModeration } from "./AdminProfilePhotoModeration";
import "./admin.css";

const accountLabel = (status: string) => status === "ACTIVE" ? "Aktif" : status === "SUSPENDED" ? "Askıya alınmış" : "Silinmiş";

export const AdminCoachesPage = () => {
  const [coaches, setCoaches] = useState<AdminCoach[]>([]);
  const [filter, setFilter] = useState<CoachFilter>("ALL");
  const [search, setSearch] = useState("");
  const [submitted, setSubmitted] = useState("");
  const [selected, setSelected] = useState<AdminCoach | null>(null);
  const [students, setStudents] = useState<CoachStudent[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [confirm, setConfirm] = useState<"SUSPEND" | "ACTIVATE" | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    adminApi.coaches(filter, submitted).then((response) => active && setCoaches(response.content ?? []))
      .catch(() => active && setError(true)).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [filter, submitted, version]);

  const open = async (coach: AdminCoach) => {
    setSelected(coach); setStudents([]);
    try {
      const [detail, list] = await Promise.all([adminApi.coach(coach.id), adminApi.coachStudents(coach.id)]);
      setSelected(detail); setStudents(list.content ?? []);
    } catch { setSelected(coach); }
  };

  const applyStatus = async () => {
    if (!selected || !confirm || busy || (confirm === "SUSPEND" && !reason.trim())) return;
    setBusy(true);
    try {
      if (confirm === "SUSPEND") await adminApi.suspendUser(selected.userId, reason.trim());
      else await adminApi.activateUser(selected.userId);
      setConfirm(null); setReason(""); setSelected(null); setLoading(true); setError(false); setVersion((value) => value + 1);
    } finally { setBusy(false); }
  };

  return <main className="admin-page">
    <header className="admin-page__heading"><div><p className="admin-page__eyebrow">Operasyonel yönetim</p><h1>Koçlar</h1><p>Mevcut koç profillerini, hesap durumlarını ve aktif öğrenci ilişkilerini yönetin.</p></div></header>
    <section className="admin-panel">
      <form className="admin-toolbar" onSubmit={(event) => { event.preventDefault(); setLoading(true); setError(false); setSubmitted(search.trim()); }}>
        <input aria-label="Koç ara" placeholder="Ad veya e-posta ara" value={search} onChange={(event) => setSearch(event.target.value)} />
        <select aria-label="Koç durumu" value={filter} onChange={(event) => { setLoading(true); setError(false); setFilter(event.target.value as CoachFilter); }}><option value="ALL">Tüm koçlar</option><option value="APPROVED">Aktif/onaylı</option><option value="SUSPENDED">Askıya alınmış</option><option value="PENDING">Profil onayı bekleyen</option><option value="REJECTED">Reddedilen</option></select>
        <button className="admin-button">Ara</button>
      </form>
      {error ? <div className="admin-error">Koçlar alınamadı.</div> : loading ? <div className="admin-loading">Koçlar yükleniyor…</div> : coaches.length === 0 ? <div className="admin-empty">Bu filtrelerle eşleşen koç bulunmuyor.</div> : <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Koç</th><th>Eğitim</th><th>Profil</th><th>Hesap</th><th></th></tr></thead><tbody>{coaches.map((coach) => <tr key={coach.id}><td><strong>{coach.name}</strong><small>{coach.email}</small></td><td>{coach.university ?? "—"}<small>{coach.department ?? "Bölüm belirtilmemiş"}</small></td><td><span className="admin-badge">{coach.approvalState}</span></td><td><span className={`admin-badge ${coach.accountStatus === "ACTIVE" ? "admin-badge--success" : "admin-badge--warning"}`}>{accountLabel(coach.accountStatus)}</span></td><td><button className="admin-button admin-button--secondary" onClick={() => void open(coach)}>Detay</button></td></tr>)}</tbody></table></div>}
    </section>
    {selected && <div className="admin-modal-backdrop" role="presentation"><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="coach-detail-title">
      <div className="admin-panel__heading"><h2 id="coach-detail-title">{selected.name}</h2><button className="admin-button admin-button--secondary" onClick={() => setSelected(null)}>Kapat</button></div>
      <div className="admin-detail"><div className="admin-detail__facts"><div><span>Hesap</span><strong>{accountLabel(selected.accountStatus)}</strong></div><div><span>Profil</span><strong>{selected.approvalState}</strong></div><div><span>Üniversite</span><strong>{selected.university ?? "—"}</strong></div><div><span>Bölüm</span><strong>{selected.department ?? "—"}</strong></div></div>
        <AdminProfilePhotoModeration name={selected.name} imageUrl={selected.profileImageUrl} assetId={selected.profileImageAssetId} onRemoved={() => setSelected((current) => current ? { ...current, profileImageUrl: null, profileImageAssetId: null } : current)} />
        <section><h3>Aktif Öğrenciler</h3>{students.length === 0 ? <div className="admin-empty">Aktif öğrenci ilişkisi bulunmuyor.</div> : <div className="admin-list">{students.map((student) => <article className="admin-list-item" key={student.studentId}><div><h3>{student.displayName}</h3><p>{student.packageName} · {student.subscriptionStatus}</p></div>{student.nextSession && <span>{new Date(student.nextSession.startTime).toLocaleDateString("tr-TR")}</span>}</article>)}</div>}</section>
        <div className="admin-actions">{selected.accountStatus === "SUSPENDED" ? <button className="admin-button" onClick={() => setConfirm("ACTIVATE")}>Hesabı Aktifleştir</button> : <button className="admin-button admin-button--danger" onClick={() => setConfirm("SUSPEND")}>Koçu Askıya Al</button>}</div>
        {confirm && <div className="admin-form"><p>{confirm === "SUSPEND" ? "Bu koç hesabı askıya alınacak. Devam etmek istediğinizi doğrulayın." : "Bu koç hesabı yeniden aktifleştirilecek."}</p>{confirm === "SUSPEND" && <label>Gerekçe<textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} required /></label>}<div className="admin-actions"><button className="admin-button admin-button--secondary" onClick={() => setConfirm(null)}>Vazgeç</button><button className="admin-button admin-button--danger" disabled={busy || (confirm === "SUSPEND" && !reason.trim())} onClick={() => void applyStatus()}>{busy ? "İşleniyor…" : "Onayla"}</button></div></div>}
      </div>
    </section></div>}
  </main>;
};
