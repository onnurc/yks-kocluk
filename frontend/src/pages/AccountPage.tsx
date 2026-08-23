/* eslint-disable react-hooks/set-state-in-effect -- profile loading is tied to the authenticated role */
import { useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "../auth/AuthProvider";
import { accountApi } from "../account/accountApi";
import type { CoachProfileResponse, StudentProfileResponse } from "../account/accountTypes";
import "./account-page.css";

type StudentDraft = {
  gradeLevel: string;
  examYear: string;
  yksScoreType: string;
  examSession: string;
  targetUniversity: string;
  targetDepartment: string;
};
type CoachDraft = { university: string; department: string; yksRanking: string };

const initials = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toLocaleUpperCase("tr-TR");
const studentDraft = (profile: StudentProfileResponse): StudentDraft => ({
  gradeLevel: profile.gradeLevel ?? "",
  examYear: profile.examYear ? String(profile.examYear) : "",
  yksScoreType: profile.yksScoreType ?? "",
  examSession: profile.examSession ?? "",
  targetUniversity: profile.targetUniversity ?? "",
  targetDepartment: profile.targetDepartment ?? "",
});
const coachDraft = (profile: CoachProfileResponse): CoachDraft => ({ university: profile.universityName ?? "", department: profile.department ?? "", yksRanking: profile.yksRanking ? String(profile.yksRanking) : "" });

export function AccountPage() {
  const { user } = useAuth();
  const fileInput = useRef<HTMLInputElement>(null);
  const [studentProfile, setStudentProfile] = useState<StudentProfileResponse | null>(null);
  const [coachProfile, setCoachProfile] = useState<CoachProfileResponse | null>(null);
  const [student, setStudent] = useState<StudentDraft>({ gradeLevel: "", examYear: "", yksScoreType: "", examSession: "", targetUniversity: "", targetDepartment: "" });
  const [coach, setCoach] = useState<CoachDraft>({ university: "", department: "", yksRanking: "" });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [version, setVersion] = useState(0);

  useEffect(() => {
    if (!user || user.role === "ADMIN") return;
    let active = true;
    setLoading(true);
    setError(null);
    const load = user.role === "STUDENT" ? accountApi.getStudentProfile() : accountApi.getCoachProfile();
    load.then((profile) => {
      if (!active) return;
      if (user.role === "STUDENT") {
        const value = profile as StudentProfileResponse;
        setStudentProfile(value);
        setStudent(studentDraft(value));
      } else {
        const value = profile as CoachProfileResponse;
        setCoachProfile(value);
        setCoach(coachDraft(value));
      }
    }).catch(() => active && setError("Hesap bilgileriniz şu anda alınamadı.")).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [user, version]);

  const profileImageUrl = studentProfile?.profileImageUrl ?? coachProfile?.profileImageUrl ?? null;
  const dirty = useMemo(() => {
    if (user?.role === "STUDENT" && studentProfile) return JSON.stringify(student) !== JSON.stringify(studentDraft(studentProfile));
    if (user?.role === "COACH" && coachProfile) return JSON.stringify(coach) !== JSON.stringify(coachDraft(coachProfile));
    return false;
  }, [coach, coachProfile, student, studentProfile, user?.role]);

  const reset = () => {
    if (studentProfile) setStudent(studentDraft(studentProfile));
    if (coachProfile) setCoach(coachDraft(coachProfile));
    setError(null);
    setNotice(null);
  };

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!user || user.role === "ADMIN") return;
    setSaving(true); setError(null); setNotice(null);
    try {
      if (user.role === "STUDENT") {
        const updated = await accountApi.updateStudentProfile({
          gradeLevel: student.gradeLevel || null,
          city: studentProfile?.city ?? null,
          examYear: student.examYear ? Number(student.examYear) : null,
          yksScoreType: (student.yksScoreType || null) as StudentProfileResponse["yksScoreType"],
          examSession: (student.examSession || null) as StudentProfileResponse["examSession"],
          targetUniversity: student.targetUniversity.trim() || null,
          targetDepartment: student.targetDepartment.trim() || null,
        });
        setStudentProfile(updated); setStudent(studentDraft(updated));
      } else if (coachProfile) {
        const updated = await accountApi.updateCoachEducation({
          university: coach.university.trim(),
          department: coach.department.trim() || null,
          yksRanking: coach.yksRanking ? Number(coach.yksRanking) : null,
        });
        setCoachProfile(updated); setCoach(coachDraft(updated));
      }
      setNotice("Bilgileriniz kaydedildi.");
    } catch { setError("Bilgileriniz kaydedilemedi. Alanları kontrol edip yeniden deneyin."); }
    finally { setSaving(false); }
  };

  const uploadPhoto = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (![/^image\/(jpeg|png|webp)$/.test(file.type), file.size <= 2 * 1024 * 1024].every(Boolean)) {
      setError("Profil fotoğrafı JPG, PNG veya WEBP olmalı ve 2 MB’ı geçmemeli."); return;
    }
    setUploading(true); setError(null); setNotice(null);
    try { await accountApi.uploadProfileImage(file); setNotice("Profil fotoğrafınız güncellendi."); setVersion((value) => value + 1); }
    catch { setError("Profil fotoğrafı yüklenemedi. Lütfen yeniden deneyin."); }
    finally { setUploading(false); }
  };

  if (!user || user.role === "ADMIN") return <div className="account-page__state">Hesap sayfasına yönlendiriliyor…</div>;

  return (
    <div className="account-page">
      <header className="account-page__heading"><p>Profil ve hesap</p><h1>Hesabım</h1><span>Üründe desteklenen kişisel ve eğitim bilgilerinizi yönetin.</span></header>
      {loading ? <div className="account-page__state" aria-busy="true">Hesap bilgileriniz yükleniyor…</div> : error && !studentProfile && !coachProfile ? (
        <div className="account-page__state account-page__state--error" role="alert"><p>{error}</p><button type="button" onClick={() => setVersion((value) => value + 1)}>Yeniden dene</button></div>
      ) : (
        <form className="account-page__card" onSubmit={save}>
          <section className="account-page__photo" aria-labelledby="profile-photo-title">
            <div className="account-page__avatar">{profileImageUrl ? <img src={profileImageUrl} alt="Profil fotoğrafı" /> : <span>{initials(user.fullName)}</span>}</div>
            <div><h2 id="profile-photo-title">Profil Fotoğrafı</h2><p>JPG, PNG veya WEBP · En fazla 2 MB</p><button type="button" disabled={uploading} onClick={() => fileInput.current?.click()}>{uploading ? "Yükleniyor…" : "Fotoğraf Yükle"}</button><input ref={fileInput} hidden type="file" accept="image/jpeg,image/png,image/webp" onChange={uploadPhoto} /></div>
          </section>

          <section className="account-page__section" aria-labelledby="personal-info-title">
            <div className="account-page__section-title"><span aria-hidden="true">○</span><div><p>Genel bilgiler</p><h2 id="personal-info-title">Kişisel Bilgiler</h2></div></div>
            <div className="account-page__fields account-page__fields--personal">
              <label>Ad Soyad<input value={user.fullName} readOnly aria-readonly="true" /></label>
              <label>E-posta Adresi<input type="email" value={user.email} readOnly aria-readonly="true" /></label>
            </div>
            <p className="account-page__read-only-note">Ad soyad ve e-posta bu hesap ekranından değiştirilemez.</p>
          </section>

          {user.role === "STUDENT" ? (
            <section className="account-page__section" aria-labelledby="student-education-title">
              <div className="account-page__section-title"><span aria-hidden="true">◇</span><div><p>Öğrenci profili</p><h2 id="student-education-title">Eğitim Hedefleri</h2></div></div>
              <div className="account-page__fields">
                <label>Sınıf Düzeyi<select value={student.gradeLevel} required onChange={(event) => setStudent((value) => ({ ...value, gradeLevel: event.target.value }))}><option value="" disabled>Seçiniz</option><option>9. Sınıf</option><option>10. Sınıf</option><option>11. Sınıf</option><option>12. Sınıf</option></select></label>
                <label>Sınava Gireceğin Yıl<input type="number" min="2024" max="2100" inputMode="numeric" value={student.examYear} onChange={(event) => setStudent((value) => ({ ...value, examYear: event.target.value }))} /></label>
                <label>YKS Puan Türü<select value={student.yksScoreType} onChange={(event) => setStudent((value) => ({ ...value, yksScoreType: event.target.value }))}><option value="">Seçiniz</option><option value="EQUAL_WEIGHT">Eşit Ağırlık (EA)</option><option value="NUMERICAL">Sayısal (SAY)</option><option value="VERBAL">Sözel (SÖZ)</option><option value="LANGUAGE">Yabancı Dil (DİL)</option></select></label>
                <label>Sınav Oturumu<select value={student.examSession} onChange={(event) => setStudent((value) => ({ ...value, examSession: event.target.value }))}><option value="">Seçiniz</option><option value="TYT">TYT (Temel Yeterlilik Testi)</option><option value="AYT">AYT (Alan Yeterlilik Testi)</option><option value="YDT">YDT (Yabancı Dil Testi)</option></select></label>
                <label>Hedef Üniversite<input value={student.targetUniversity} maxLength={200} onChange={(event) => setStudent((value) => ({ ...value, targetUniversity: event.target.value }))} /></label>
                <label>Hedef Bölüm<input value={student.targetDepartment} maxLength={150} onChange={(event) => setStudent((value) => ({ ...value, targetDepartment: event.target.value }))} /></label>
              </div>
            </section>
          ) : (
            <section className="account-page__section" aria-labelledby="coach-education-title">
              <div className="account-page__section-title"><span aria-hidden="true">◇</span><div><p>Koç profili</p><h2 id="coach-education-title">Eğitim Bilgilerim</h2></div></div>
              <div className="account-page__fields">
                <label>Üniversite<input type="text" value={coach.university} required maxLength={200} onChange={(event) => setCoach((value) => ({ ...value, university: event.target.value }))} /></label>
                <label>Bölüm<input value={coach.department} maxLength={150} onChange={(event) => setCoach((value) => ({ ...value, department: event.target.value }))} /></label>
                <label>YKS Sıralaması<input type="number" min="1" inputMode="numeric" value={coach.yksRanking} onChange={(event) => setCoach((value) => ({ ...value, yksRanking: event.target.value }))} /></label>
              </div>
            </section>
          )}

          {(error || notice) && <p className={`account-page__feedback ${error ? "is-error" : "is-success"}`} role={error ? "alert" : "status"}>{error ?? notice}</p>}
          <div className="account-page__actions"><button type="button" className="account-page__reset" disabled={!dirty || saving} onClick={reset}>Değişiklikleri Geri Al</button><button type="submit" className="account-page__save" disabled={!dirty || saving}>{saving ? "Kaydediliyor…" : "Bilgileri Kaydet"}</button></div>
        </form>
      )}
    </div>
  );
}
