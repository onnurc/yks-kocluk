import React, { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";
import { ApiError } from "../api/ApiError";
import { LegalDocumentViewer } from "../legal/LegalDocumentViewer";
import { useLegalDocuments } from "../legal/useLegalDocuments";
import { isStaleLegalDocumentError, legalErrorMessage } from "../legal/legalErrors";
import { readinessPathForUser } from "../auth/authNavigation";
import { getApiBaseUrl } from "../api/httpClient";
import { AuthPageShell } from "./AuthPageShell";
import "./auth-page.css";

const REGISTRATION_DOCUMENT_TYPES = ["TERMS_OF_USE", "EXPLICIT_CONSENT", "KVKK_NOTICE"] as const;

export const RegisterPage: React.FC = () => {
  const { register, isAuthenticated, user, isSuspended } = useAuth();
  const navigate = useNavigate();
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [error, setError] = useState<ApiError | Error | string | null>(null);
  const [loading, setLoading] = useState(false);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [explicitConsentAccepted, setExplicitConsentAccepted] = useState(false);
  const [marketingEmailOptIn, setMarketingEmailOptIn] = useState(false);
  const [marketingSmsOptIn, setMarketingSmsOptIn] = useState(false);
  const legalDocuments = useLegalDocuments([...REGISTRATION_DOCUMENT_TYPES]);

  useEffect(() => {
    if (isSuspended) navigate("/suspended");
    else if (isAuthenticated && user) navigate(readinessPathForUser(user));
  }, [isAuthenticated, user, isSuspended, navigate]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!firstName.trim() || !lastName.trim()) {
      setError("Ad ve Soyad alanları gereklidir.");
      return;
    }
    if (!email.trim() || !password.trim()) {
      setError("E-posta ve şifre gereklidir.");
      return;
    }
    if (password.length < 8) {
      setError("Şifre en az 8 karakter olmalıdır.");
      return;
    }
    if (!dateOfBirth) {
      setError("Öğrenci kaydı için doğum tarihi zorunludur.");
      return;
    }
    if (new Date(dateOfBirth) > new Date()) {
      setError("Doğum tarihi gelecekte olamaz.");
      return;
    }
    if (!legalDocuments.ready) {
      setError("Hukuki metinler yüklenemedi. Lütfen yeniden deneyin.");
      return;
    }
    if (!termsAccepted || !explicitConsentAccepted) {
      setError("Kayıt için gerekli hukuki metinleri onaylamalısınız.");
      return;
    }

    setLoading(true);
    try {
      await register({
        email,
        password,
        fullName: `${firstName.trim()} ${lastName.trim()}`,
        role: "STUDENT",
        dateOfBirth,
        acceptedTermsDocumentId: legalDocuments.documents.TERMS_OF_USE!.id,
        acceptedExplicitConsentDocumentId: legalDocuments.documents.EXPLICIT_CONSENT!.id,
        marketingEmailOptIn,
        marketingSmsOptIn,
      });
    } catch (err) {
      const message = legalErrorMessage(err);
      setError(message || (err as ApiError | Error));
      if (isStaleLegalDocumentError(err)) {
        setTermsAccepted(false);
        setExplicitConsentAccepted(false);
        await legalDocuments.reload();
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuthPageShell
      title="Uniform'a Katıl"
      lead="Hedeflerine uygun koçluk deneyimini oluşturmak için hesabını birkaç adımda tamamla."
      icon="＋"
      wide
    >
      <div className="auth-error-slot"><FormError error={error} /></div>
      <a className="auth-google" href={`${getApiBaseUrl()}/oauth2/authorization/google`}>
        <svg aria-hidden="true" width="20" height="20" viewBox="0 0 18 18">
          <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.9c1.7-1.57 2.7-3.88 2.7-6.62Z" />
          <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.9-2.26c-.81.54-1.84.86-3.06.86-2.35 0-4.34-1.58-5.05-3.71H.95v2.33A9 9 0 0 0 9 18Z" />
          <path fill="#FBBC05" d="M3.95 10.71A5.4 5.4 0 0 1 3.67 9c0-.59.1-1.17.28-1.71V4.96H.95A9 9 0 0 0 0 9c0 1.45.35 2.83.95 4.04l3-2.33Z" />
          <path fill="#EA4335" d="M9 3.58c1.32 0 2.51.45 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 0 0 .95 4.96l3 2.33C4.66 5.16 6.65 3.58 9 3.58Z" />
        </svg>
        Google ile Kayıt Ol
      </a>
      <div className="auth-divider">veya</div>
      <form className="auth-form auth-form--register" onSubmit={handleSubmit}>
        <div className="auth-form__grid">
          <label className="auth-field" htmlFor="first-name"><span>Ad:</span><input id="first-name" type="text" autoComplete="given-name" value={firstName} onChange={(event) => setFirstName(event.target.value)} required disabled={loading} /></label>
          <label className="auth-field" htmlFor="last-name"><span>Soyad:</span><input id="last-name" type="text" autoComplete="family-name" value={lastName} onChange={(event) => setLastName(event.target.value)} required disabled={loading} /></label>
        </div>
        <label className="auth-field" htmlFor="register-email"><span>E-posta:</span><input id="register-email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required disabled={loading} /></label>
        <label className="auth-field" htmlFor="register-password"><span>Şifre (Min 8 karakter):</span><input id="register-password" type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} minLength={8} required disabled={loading} /></label>
        <label className="auth-field" htmlFor="date-of-birth"><span>Doğum Tarihi:</span><input id="date-of-birth" type="date" value={dateOfBirth} onChange={(event) => setDateOfBirth(event.target.value)} required disabled={loading} /></label>

        <fieldset className="auth-legal">
          <legend>Hukuki Onaylar</legend>
          {legalDocuments.loading && <p role="status">Hukuki metinler yükleniyor…</p>}
          {legalDocuments.error && <div className="auth-legal__error" role="alert">Hukuki metinler yüklenemedi. <button type="button" onClick={() => void legalDocuments.reload()}>Yeniden Dene</button></div>}
          <p className="auth-legal__notice">Kişisel verilerinizin işlenmesine ilişkin <LegalDocumentViewer label="KVKK Aydınlatma Metni" document={legalDocuments.documents.KVKK_NOTICE} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} />’ni inceleyebilirsiniz.</p>
          <div className="auth-consent">
            <input id="accept-terms" type="checkbox" checked={termsAccepted} onChange={(event) => setTermsAccepted(event.target.checked)} required disabled={loading || !legalDocuments.ready} />
            <div><label htmlFor="accept-terms">Kullanım Koşulları’nı okudum ve kabul ediyorum. <strong>(Zorunlu)</strong></label> <LegalDocumentViewer label="Metni görüntüle" document={legalDocuments.documents.TERMS_OF_USE} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} /></div>
          </div>
          <div className="auth-consent">
            <input id="accept-explicit-consent" type="checkbox" checked={explicitConsentAccepted} onChange={(event) => setExplicitConsentAccepted(event.target.checked)} required disabled={loading || !legalDocuments.ready} />
            <div><label htmlFor="accept-explicit-consent">Açık Rıza Metni’ni okudum ve kabul ediyorum. <strong>(Zorunlu)</strong></label> <LegalDocumentViewer label="Metni görüntüle" document={legalDocuments.documents.EXPLICIT_CONSENT} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} /></div>
          </div>
          <label className="auth-consent"><input type="checkbox" checked={marketingEmailOptIn} onChange={(event) => setMarketingEmailOptIn(event.target.checked)} disabled={loading} /><span>Kampanya ve bilgilendirmeler için e-posta almak istiyorum. (İsteğe bağlı)</span></label>
          <label className="auth-consent"><input type="checkbox" checked={marketingSmsOptIn} onChange={(event) => setMarketingSmsOptIn(event.target.checked)} disabled={loading} /><span>Kampanya ve bilgilendirmeler için SMS almak istiyorum. (İsteğe bağlı)</span></label>
        </fieldset>

        <button className="auth-submit" type="submit" aria-label="Kayıt Ol" disabled={loading || !legalDocuments.ready || !termsAccepted || !explicitConsentAccepted}>{loading ? "Kayıt Yapılıyor…" : "Kayıt Ol →"}</button>
      </form>
      <p className="auth-footer">Zaten hesabın var mı? <Link to="/login">Giriş Yap</Link></p>
      <p className="auth-footer">Koç olarak katılmak mı istiyorsun? <Link to="/koc-basvuru">Başvuru formunu doldur</Link></p>
      <p className="auth-security"><span aria-hidden="true">♙</span> Hukuki tercihleriniz ayrı ayrı ve güvenli biçimde kaydedilir.</p>
    </AuthPageShell>
  );
};
