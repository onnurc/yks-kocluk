import { useEffect, useRef, useState } from "react";
import type { FormEvent } from "react";
import { adminApi } from "../../admin/adminApi";
import type { AdminCoach, CoachFilter, CoachStudent } from "../../admin/adminTypes";
import { AdminProfilePhotoModeration } from "./AdminProfilePhotoModeration";
import { AdminCoachVideoEditor } from "./AdminCoachVideoEditor";
import { emailMessageFromApiError, GENERIC_OPERATION_ERROR, validateEmailForUx } from "../../validation/emailValidation";
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
  const [notice, setNotice] = useState("");
  const [creating, setCreating] = useState(false);
  const [createName, setCreateName] = useState("");
  const [createEmail, setCreateEmail] = useState("");
  const [createEmailError, setCreateEmailError] = useState<string | null>(null);
  const createEmailInputRef = useRef<HTMLInputElement>(null);
  const [profileAction, setProfileAction] = useState<"APPROVE" | "REJECT" | null>(null);

  useEffect(() => {
    let active = true;
    adminApi.coaches(filter, submitted).then((response) => active && setCoaches(response.content ?? []))
      .catch(() => active && setError(true)).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [filter, submitted, version]);

  useEffect(() => {
    if (createEmailError) createEmailInputRef.current?.focus();
  }, [createEmailError]);

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

  const closeSelected = () => {
    setSelected(null); setConfirm(null); setProfileAction(null); setReason("");
  };

  const createCoach = async (event?: FormEvent) => {
    event?.preventDefault();
    if (busy || !createName.trim()) return;
    const nextEmailError = validateEmailForUx(createEmail);
    if (nextEmailError) {
      setCreateEmailError(nextEmailError);
      createEmailInputRef.current?.focus();
      return;
    }
    setBusy(true); setNotice("");
    setCreateEmailError(null);
    try {
      await adminApi.createCoach({ fullName: createName.trim(), email: createEmail.trim() });
      setCreating(false); setCreateName(""); setCreateEmail("");
      setNotice("Koç hesabı oluşturuldu. Parola belirleme bağlantısı koça e-posta ile gönderildi.");
      setLoading(true); setError(false); setVersion((value) => value + 1);
    } catch (error) {
      const backendEmailError = emailMessageFromApiError(error);
      if (backendEmailError) {
        setCreateEmailError(backendEmailError);
        createEmailInputRef.current?.focus();
      } else {
        setNotice(GENERIC_OPERATION_ERROR);
      }
    }
    finally { setBusy(false); }
  };

  const applyProfileAction = async () => {
    if (!selected || !profileAction || busy || (profileAction === "REJECT" && !reason.trim())) return;
    setBusy(true); setNotice("");
    try {
      if (profileAction === "APPROVE") await adminApi.approveCoachProfile(selected.coachProfileId);
      else await adminApi.rejectCoachProfile(selected.coachProfileId, reason.trim());
      setNotice(profileAction === "APPROVE" ? "Koç profili onaylandı." : "Koç profili reddedildi.");
      setProfileAction(null); setReason(""); setSelected(null);
      setLoading(true); setError(false); setVersion((value) => value + 1);
    } catch { setNotice("Profil işlemi tamamlanamadı. Lütfen yeniden deneyin."); }
    finally { setBusy(false); }
  };

  return <main className="admin-page">
    <header className="admin-page__heading"><div><p className="admin-page__eyebrow">Operasyonel yönetim</p><h1>Koçlar</h1><p>Mevcut koç profillerini, hesap durumlarını ve aktif öğrenci ilişkilerini yönetin.</p></div><button className="admin-button" onClick={() => { setCreating(true); setNotice(""); setCreateEmailError(null); }}>Koç Ekle</button></header>
    <section className="admin-panel">
      {notice && <p className={`admin-feedback ${notice.includes("oluşturulamadı") || notice.includes("tamamlanamadı") || notice.includes("hata oluştu") ? "is-error" : ""}`} role="status">{notice}</p>}
      <form className="admin-toolbar" onSubmit={(event) => { event.preventDefault(); setLoading(true); setError(false); setSubmitted(search.trim()); }}>
        <input aria-label="Koç ara" placeholder="Ad veya e-posta ara" value={search} onChange={(event) => setSearch(event.target.value)} />
        <select aria-label="Koç durumu" value={filter} onChange={(event) => { setLoading(true); setError(false); setFilter(event.target.value as CoachFilter); }}><option value="ALL">Tüm koçlar</option><option value="APPROVED">Aktif/onaylı</option><option value="SUSPENDED">Askıya alınmış</option><option value="PENDING">Profil onayı bekleyen</option><option value="REJECTED">Reddedilen</option></select>
        <button className="admin-button">Ara</button>
      </form>
      {error ? <div className="admin-error">Koçlar alınamadı.</div> : loading ? <div className="admin-loading">Koçlar yükleniyor…</div> : coaches.length === 0 ? <div className="admin-empty">Bu filtrelerle eşleşen koç bulunmuyor.</div> : <div className="admin-table-wrap"><table className="admin-table"><thead><tr><th>Koç</th><th>Eğitim</th><th>Profil</th><th>Hesap</th><th></th></tr></thead><tbody>{coaches.map((coach) => <tr key={coach.id}><td><strong>{coach.name}</strong><small>{coach.email}</small></td><td>{coach.university ?? "—"}<small>{coach.department ?? "Bölüm belirtilmemiş"}</small></td><td><span className="admin-badge">{coach.approvalState}</span></td><td><span className={`admin-badge ${coach.accountStatus === "ACTIVE" ? "admin-badge--success" : "admin-badge--warning"}`}>{accountLabel(coach.accountStatus)}</span></td><td><button className="admin-button admin-button--secondary" onClick={() => void open(coach)}>Detay</button></td></tr>)}</tbody></table></div>}
    </section>
    {selected && <div className="admin-modal-backdrop" role="presentation"><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="coach-detail-title">
      <div className="admin-panel__heading"><h2 id="coach-detail-title">{selected.name}</h2><button className="admin-button admin-button--secondary" onClick={closeSelected}>Kapat</button></div>
      <div className="admin-detail"><div className="admin-detail__facts"><div><span>Hesap</span><strong>{accountLabel(selected.accountStatus)}</strong></div><div><span>Profil</span><strong>{selected.approvalState}</strong></div><div><span>Üniversite</span><strong>{selected.university ?? "—"}</strong></div><div><span>Bölüm</span><strong>{selected.department ?? "—"}</strong></div></div>
        <AdminProfilePhotoModeration name={selected.name} imageUrl={selected.profileImageUrl} assetId={selected.profileImageAssetId} onRemoved={() => setSelected((current) => current ? { ...current, profileImageUrl: null, profileImageAssetId: null } : current)} />
        <AdminCoachVideoEditor coach={selected} onUpdated={setSelected} />
        <section><h3>Aktif Öğrenciler</h3>{students.length === 0 ? <div className="admin-empty">Aktif öğrenci ilişkisi bulunmuyor.</div> : <div className="admin-list">{students.map((student) => <article className="admin-list-item" key={student.studentId}><div><h3>{student.displayName}</h3><p>{student.packageName} · {student.subscriptionStatus}</p></div>{student.nextSession && <span>{new Date(student.nextSession.startTime).toLocaleDateString("tr-TR")}</span>}</article>)}</div>}</section>
        <div className="admin-actions">{selected.approvalState === "PENDING" && <><button className="admin-button" onClick={() => { setConfirm(null); setProfileAction("APPROVE"); setReason(""); }}>Profili Onayla</button><button className="admin-button admin-button--danger" onClick={() => { setConfirm(null); setProfileAction("REJECT"); setReason(""); }}>Profili Reddet</button></>}{selected.accountStatus === "SUSPENDED" ? <button className="admin-button" onClick={() => { setProfileAction(null); setConfirm("ACTIVATE"); }}>Hesabı Aktifleştir</button> : <button className="admin-button admin-button--danger" onClick={() => { setProfileAction(null); setConfirm("SUSPEND"); }}>Koçu Askıya Al</button>}</div>
        {profileAction && <div className="admin-form"><p>{profileAction === "APPROVE" ? "Bu koç profili herkese açık ve rezervasyona uygun hale gelecek." : "Profili reddetmek için açık bir gerekçe girin."}</p>{profileAction === "REJECT" && <label>Profil red gerekçesi<textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} required /></label>}<div className="admin-actions"><button className="admin-button admin-button--secondary" onClick={() => { setProfileAction(null); setReason(""); }}>Vazgeç</button><button className={`admin-button ${profileAction === "REJECT" ? "admin-button--danger" : ""}`} disabled={busy || (profileAction === "REJECT" && !reason.trim())} onClick={() => void applyProfileAction()}>{busy ? "İşleniyor…" : profileAction === "APPROVE" ? "Profil Onayını Doğrula" : "Profili Reddet"}</button></div></div>}
        {confirm && <div className="admin-form"><p>{confirm === "SUSPEND" ? "Bu koç hesabı askıya alınacak. Devam etmek istediğinizi doğrulayın." : "Bu koç hesabı yeniden aktifleştirilecek."}</p>{confirm === "SUSPEND" && <label>Gerekçe<textarea rows={3} value={reason} onChange={(event) => setReason(event.target.value)} required /></label>}<div className="admin-actions"><button className="admin-button admin-button--secondary" onClick={() => setConfirm(null)}>Vazgeç</button><button className="admin-button admin-button--danger" disabled={busy || (confirm === "SUSPEND" && !reason.trim())} onClick={() => void applyStatus()}>{busy ? "İşleniyor…" : "Onayla"}</button></div></div>}
      </div>
    </section></div>}
    {creating && <div className="admin-modal-backdrop" role="presentation"><section className="admin-modal" role="dialog" aria-modal="true" aria-labelledby="create-coach-title"><div className="admin-panel__heading"><h2 id="create-coach-title">Koç Ekle</h2><button type="button" className="admin-button admin-button--secondary" onClick={() => setCreating(false)}>Kapat</button></div><form className="admin-form" onSubmit={(event) => void createCoach(event)} noValidate><p>Koç için güvenli parola belirleme bağlantısı e-posta ile gönderilecektir.</p><label>Ad Soyad<input value={createName} maxLength={150} onChange={(event) => setCreateName(event.target.value)} required /></label><div className="admin-field-group"><label htmlFor="admin-create-coach-email">E-posta</label><input id="admin-create-coach-email" ref={createEmailInputRef} type="email" inputMode="email" autoComplete="email" value={createEmail} maxLength={255} onChange={(event) => { setCreateEmail(event.target.value); if (createEmailError) setCreateEmailError(null); }} aria-invalid={createEmailError ? true : undefined} aria-describedby={createEmailError ? "admin-create-coach-email-error" : undefined} required />{createEmailError && <span className="admin-field-error" id="admin-create-coach-email-error" role="alert">{createEmailError}</span>}</div><div className="admin-actions"><button type="button" className="admin-button admin-button--secondary" onClick={() => setCreating(false)}>Vazgeç</button><button type="submit" className="admin-button" disabled={busy || !createName.trim()}>{busy ? "Oluşturuluyor…" : "Koç Hesabı Oluştur"}</button></div></form></section></div>}
  </main>;
};
