/* eslint-disable react-hooks/set-state-in-effect -- profile loading is tied to the authenticated role */
import { useEffect, useMemo, useRef, useState } from "react";
import type { KeyboardEvent as ReactKeyboardEvent, MouseEvent as ReactMouseEvent } from "react";
import { useAuth } from "../auth/AuthProvider";
import { accountApi } from "../account/accountApi";
import { useAccountProfile } from "../account/accountProfileContext";
import type { CoachProfileResponse, StudentProfileResponse } from "../account/accountTypes";
import { mediaErrorMessage } from "../account/mediaErrors";
import { isSupportedProfileImage, PROFILE_IMAGE_MAX_LABEL } from "../account/mediaPolicy";
import "./account-page.css";

type StudentDraft = {
  gradeLevel: string;
  examYear: string;
  yksScoreType: "" | NonNullable<StudentProfileResponse["yksScoreType"]>;
  examSession: "" | NonNullable<StudentProfileResponse["examSession"]>;
  targetUniversity: string;
  targetDepartment: string;
};
type CoachDraft = { university: string; department: string; yksRanking: string; bio: string };

const COACH_BIO_MAX_LENGTH = 1000;

const initials = (name: string) => name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0]).join("").toLocaleUpperCase("tr-TR");
const studentDraft = (profile: StudentProfileResponse): StudentDraft => ({
  gradeLevel: profile.gradeLevel ?? "",
  examYear: profile.examYear ? String(profile.examYear) : "",
  yksScoreType: profile.yksScoreType ?? "",
  examSession: profile.examSession ?? "",
  targetUniversity: profile.targetUniversity ?? "",
  targetDepartment: profile.targetDepartment ?? "",
});
const coachDraft = (profile: CoachProfileResponse): CoachDraft => ({ university: profile.universityName ?? "", department: profile.department ?? "", yksRanking: profile.yksRanking ? String(profile.yksRanking) : "", bio: profile.bio ?? "" });
const scoreTypeDraft = (value: string): StudentDraft["yksScoreType"] => {
  if (value === "EQUAL_WEIGHT" || value === "NUMERICAL" || value === "VERBAL" || value === "LANGUAGE") return value;
  return "";
};
const examSessionDraft = (value: string): StudentDraft["examSession"] => {
  if (value === "TYT" || value === "AYT" || value === "YDT") return value;
  return "";
};

type ProfilePhotoRemovalModalProps = {
  busy: boolean;
  error: string | null;
  onCancel: () => void;
  onConfirm: () => void;
};

function ProfilePhotoRemovalModal({ busy, error, onCancel, onConfirm }: ProfilePhotoRemovalModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    cancelRef.current?.focus();
    return () => previouslyFocused?.focus();
  }, []);

  const handleKeyDown = (event: ReactKeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape" && !busy) {
      event.preventDefault();
      onCancel();
      return;
    }
    if (event.key !== "Tab") return;
    const focusable = dialogRef.current?.querySelectorAll<HTMLButtonElement>("button:not(:disabled)");
    if (!focusable?.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  };

  const handleBackdropClick = (event: ReactMouseEvent<HTMLDivElement>) => {
    if (event.target === event.currentTarget && !busy) onCancel();
  };

  return (
    <div className="account-photo-modal__backdrop" onClick={handleBackdropClick} onKeyDown={handleKeyDown}>
      <div ref={dialogRef} className="account-photo-modal" role="dialog" aria-modal="true" aria-labelledby="profile-photo-remove-title" aria-describedby="profile-photo-remove-message" aria-busy={busy}>
        <p className="account-photo-modal__eyebrow">Profil işlemi</p>
        <h2 id="profile-photo-remove-title">Profil Fotoğrafını Kaldır</h2>
        <p id="profile-photo-remove-message">Profil fotoğrafınızı kaldırmak istediğinize emin misiniz?</p>
        {error && <p className="account-photo-modal__error" role="alert">{error}</p>}
        <div className="account-photo-modal__actions">
          <button ref={cancelRef} type="button" className="account-photo-modal__cancel" disabled={busy} onClick={onCancel}>İptal</button>
          <button type="button" className="account-photo-modal__confirm" disabled={busy} onClick={onConfirm}>{busy ? "Kaldırılıyor…" : "Fotoğrafı Kaldır"}</button>
        </div>
      </div>
    </div>
  );
}

export function AccountPage() {
  const { user } = useAuth();
  const {
    studentProfile,
    coachProfile,
    profileLoading: loading,
    profileLoadFailed,
    refreshProfile,
    setStudentProfile,
    setCoachProfile,
  } = useAccountProfile();
  const fileInput = useRef<HTMLInputElement>(null);
  const [student, setStudent] = useState<StudentDraft>({ gradeLevel: "", examYear: "", yksScoreType: "", examSession: "", targetUniversity: "", targetDepartment: "" });
  const [coach, setCoach] = useState<CoachDraft>({ university: "", department: "", yksRanking: "", bio: "" });
  const [saving, setSaving] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [removingPhoto, setRemovingPhoto] = useState(false);
  const [removePhotoModalOpen, setRemovePhotoModalOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [failedProfileImageUrl, setFailedProfileImageUrl] = useState<string | null>(null);

  useEffect(() => {
    if (studentProfile) setStudent(studentDraft(studentProfile));
    if (coachProfile) setCoach(coachDraft(coachProfile));
  }, [coachProfile, studentProfile]);

  // profileImageUrl points at the backend's own /api/v1/public/media/{opaqueToken} redirect, which
  // signs a fresh storage URL per request — so it stays valid however long this page is open.
  const profileImageUrl = studentProfile?.profileImageUrl ?? coachProfile?.profileImageUrl ?? null;
  const profileImageAssetId = studentProfile?.profileImageAssetId ?? coachProfile?.profileImageAssetId ?? null;
  const availableProfileImageUrl = profileImageUrl && profileImageUrl !== failedProfileImageUrl ? profileImageUrl : null;
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
    if (user.role === "COACH" && coach.bio.length > COACH_BIO_MAX_LENGTH) {
      setError(`Biyografi en fazla ${COACH_BIO_MAX_LENGTH} karakter olabilir.`);
      setNotice(null);
      return;
    }
    setSaving(true); setError(null); setNotice(null);
    try {
      if (user.role === "STUDENT") {
        const updated = await accountApi.updateStudentProfile({
          gradeLevel: student.gradeLevel || null,
          city: studentProfile?.city ?? null,
          examYear: student.examYear ? Number(student.examYear) : null,
          yksScoreType: student.yksScoreType || null,
          examSession: student.examSession || null,
          targetUniversity: student.targetUniversity.trim() || null,
          targetDepartment: student.targetDepartment.trim() || null,
        });
        setStudentProfile(updated); setStudent(studentDraft(updated));
      } else if (coachProfile) {
        const updated = await accountApi.updateCoachEducation({
          university: coach.university.trim(),
          department: coach.department.trim() || null,
          yksRanking: coach.yksRanking ? Number(coach.yksRanking) : null,
          bio: coach.bio.trim() || null,
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
    if (!isSupportedProfileImage(file)) {
      setError(`Profil fotoğrafı JPG, PNG veya WEBP olmalı ve ${PROFILE_IMAGE_MAX_LABEL}’ı geçmemeli.`); return;
    }
    setUploading(true); setError(null); setNotice(null);
    try { await accountApi.uploadProfileImage(file); await refreshProfile(); setNotice("Profil fotoğrafınız güncellendi."); }
    catch (caught) { setError(mediaErrorMessage(caught, "Profil fotoğrafı yüklenemedi. Lütfen yeniden deneyin.")); }
    finally { setUploading(false); }
  };

  const openRemovePhotoModal = () => {
    if (profileImageAssetId == null || removingPhoto) return;
    setError(null);
    setNotice(null);
    setRemovePhotoModalOpen(true);
  };

  const removePhoto = async () => {
    const assetId = profileImageAssetId;
    if (assetId == null || removingPhoto) return;
    setRemovingPhoto(true); setError(null); setNotice(null);
    try {
      await accountApi.deleteMedia(assetId);
      if (studentProfile) {
        setStudentProfile((prev) => prev ? { ...prev, profileImageAssetId: null, profileImageUrl: null } : prev);
      } else if (coachProfile) {
        setCoachProfile((prev) => prev ? { ...prev, profileImageAssetId: null, profileImageUrl: null } : prev);
      }
      setRemovePhotoModalOpen(false);
      setNotice("Profil fotoğrafınız kaldırıldı.");
    } catch (caught) { setError(mediaErrorMessage(caught, "Profil fotoğrafı kaldırılamadı. Lütfen yeniden deneyin.")); }
    finally { setRemovingPhoto(false); }
  };

  if (!user || user.role === "ADMIN") return <div className="account-page__state">Hesap sayfasına yönlendiriliyor…</div>;

  return (
    <div className="account-page">
      <header className="account-page__heading"><p>Profil ve hesap</p><h1>Hesabım</h1><span>Üründe desteklenen kişisel ve eğitim bilgilerinizi yönetin.</span></header>
      {loading && !studentProfile && !coachProfile ? <div className="account-page__state" aria-busy="true">Hesap bilgileriniz yükleniyor…</div> : profileLoadFailed && !studentProfile && !coachProfile ? (
        <div className="account-page__state account-page__state--error" role="alert"><p>Hesap bilgileriniz şu anda alınamadı.</p><button type="button" onClick={() => void refreshProfile()}>Yeniden dene</button></div>
      ) : (
        <form className="account-page__card" onSubmit={save}>
          <section className="account-page__photo" aria-labelledby="profile-photo-title">
            <div className="account-page__avatar">{availableProfileImageUrl ? <img src={availableProfileImageUrl} alt="Profil fotoğrafı" onError={() => setFailedProfileImageUrl(availableProfileImageUrl)} /> : <span>{initials(user.fullName)}</span>}</div>
            <div>
              <h2 id="profile-photo-title">Profil Fotoğrafı</h2>
              <p>JPG, PNG veya WEBP · En fazla {PROFILE_IMAGE_MAX_LABEL}</p>
              <div className="account-page__photo-actions">
                <button type="button" disabled={uploading || removingPhoto} onClick={() => fileInput.current?.click()}>{uploading ? "Yükleniyor…" : "Fotoğraf Yükle"}</button>
                {profileImageAssetId != null && (
                  <button
                    type="button"
                    className="account-page__photo-remove"
                    disabled={uploading || removingPhoto}
                    onClick={openRemovePhotoModal}
                  >
                    {removingPhoto ? "Kaldırılıyor…" : "Fotoğrafı Kaldır"}
                  </button>
                )}
              </div>
              <input ref={fileInput} hidden type="file" accept="image/jpeg,image/png,image/webp" onChange={uploadPhoto} />
            </div>
          </section>

          <section className="account-page__section" aria-labelledby="personal-info-title">
            <div className="account-page__section-title"><span aria-hidden="true">○</span><div><p>Genel bilgiler</p><h2 id="personal-info-title">Kişisel Bilgiler</h2></div></div>
            <div className="account-page__fields account-page__fields--personal">
              <label className="account-page__readonly-field">Ad Soyad<input value={user.fullName} readOnly aria-readonly="true" /></label>
              <label className="account-page__readonly-field">E-posta Adresi<input type="email" value={user.email} readOnly aria-readonly="true" /></label>
            </div>
            <p className="account-page__read-only-note">Ad soyad ve e-posta bu hesap ekranından değiştirilemez.</p>
          </section>

          {user.role === "STUDENT" ? (
            <section className="account-page__section" aria-labelledby="student-education-title">
              <div className="account-page__section-title"><span aria-hidden="true">◇</span><div><p>Öğrenci profili</p><h2 id="student-education-title">Eğitim Hedefleri</h2></div></div>
              <div className="account-page__fields">
                <label>Sınıf Düzeyi<select value={student.gradeLevel} required onChange={(event) => setStudent((value) => ({ ...value, gradeLevel: event.target.value }))}><option value="" disabled>Seçiniz</option><option>9. Sınıf</option><option>10. Sınıf</option><option>11. Sınıf</option><option>12. Sınıf</option></select></label>
                <label>Sınava Gireceğin Yıl<input type="number" min="2024" max="2100" inputMode="numeric" value={student.examYear} onChange={(event) => setStudent((value) => ({ ...value, examYear: event.target.value }))} /></label>
                <label>YKS Puan Türü<select value={student.yksScoreType} onChange={(event) => setStudent((value) => ({ ...value, yksScoreType: scoreTypeDraft(event.target.value) }))}><option value="">Seçiniz</option><option value="EQUAL_WEIGHT">Eşit Ağırlık (EA)</option><option value="NUMERICAL">Sayısal (SAY)</option><option value="VERBAL">Sözel (SÖZ)</option><option value="LANGUAGE">Yabancı Dil (DİL)</option></select></label>
                <label>Sınav Oturumu<select value={student.examSession} onChange={(event) => setStudent((value) => ({ ...value, examSession: examSessionDraft(event.target.value) }))}><option value="">Seçiniz</option><option value="TYT">TYT (Temel Yeterlilik Testi)</option><option value="AYT">AYT (Alan Yeterlilik Testi)</option><option value="YDT">YDT (Yabancı Dil Testi)</option></select></label>
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
                <label className="account-page__biography">Hakkımda<textarea aria-label="Hakkımda" value={coach.bio} maxLength={COACH_BIO_MAX_LENGTH} aria-describedby="coach-biography-help coach-biography-count" onChange={(event) => setCoach((value) => ({ ...value, bio: event.target.value }))} /><span className="account-page__field-meta"><small id="coach-biography-help">Public koç profilinizin Hakkında bölümünde gösterilir.</small><small id="coach-biography-count" aria-live="polite">{coach.bio.length} / {COACH_BIO_MAX_LENGTH}</small></span></label>
              </div>
            </section>
          )}

          {(error || notice) && !removePhotoModalOpen && <p className={`account-page__feedback ${error ? "is-error" : "is-success"}`} role={error ? "alert" : "status"}>{error ?? notice}</p>}
          <div className="account-page__actions"><button type="button" className="account-page__reset" disabled={!dirty || saving} onClick={reset}>Değişiklikleri Geri Al</button><button type="submit" className="account-page__save" disabled={!dirty || saving}>{saving ? "Kaydediliyor…" : "Bilgileri Kaydet"}</button></div>
        </form>
      )}
      {removePhotoModalOpen && (
        <ProfilePhotoRemovalModal
          busy={removingPhoto}
          error={error}
          onCancel={() => setRemovePhotoModalOpen(false)}
          onConfirm={() => void removePhoto()}
        />
      )}
    </div>
  );
}
