import React, { useEffect, useRef, useState } from "react";
import { coachApplicationApi } from "./coachApplicationApi";
import { emailMessageFromApiError, GENERIC_OPERATION_ERROR, validateEmailForUx } from "../validation/emailValidation";
import "./coach-application-page.css";

export const CoachApplicationPage: React.FC = () => {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [emailError, setEmailError] = useState<string | null>(null);
  const emailInputRef = useRef<HTMLInputElement>(null);
  const [phone, setPhone] = useState("");
  const [experience, setExperience] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    if (emailError) emailInputRef.current?.focus();
  }, [emailError]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setEmailError(null);
    if (!fullName.trim()) {
      setError("Ad Soyad alanı zorunludur.");
      return;
    }
    const nextEmailError = validateEmailForUx(email);
    if (nextEmailError) {
      setEmailError(nextEmailError);
      emailInputRef.current?.focus();
      return;
    }
    setLoading(true);
    try {
      await coachApplicationApi.submit({
        fullName: fullName.trim(),
        email: email.trim(),
        phone: phone.trim() || undefined,
        experience: experience.trim() || undefined,
      });
      setSubmitted(true);
    } catch (err) {
      const backendEmailError = emailMessageFromApiError(err);
      if (backendEmailError) {
        setEmailError(backendEmailError);
        emailInputRef.current?.focus();
      } else {
        setError(GENERIC_OPERATION_ERROR);
      }
    } finally {
      setLoading(false);
    }
  };

  if (submitted) {
    return (
      <div className="coach-application-page">
        <div className="coach-application-page__inner coach-application-page__success">
          <h1>Başvurunuz alındı</h1>
          <p>
            İlginiz için teşekkür ederiz. Ekibimiz başvurunuzu inceleyecek ve uygun bulunması
            durumunda belirttiğiniz e-posta adresine hesap bilgileriniz iletilecektir.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="coach-application-page">
      <div className="coach-application-page__inner">
        <h1>Koç Olarak Başvur</h1>
        <p className="coach-application-page__lead">
          Uniform Akademi'de öğrencilere mentorluk yapmak ister misin? Aşağıdaki formu doldur,
          başvurunu inceleyip sana dönüş yapalım.
        </p>

        {error && <div className="coach-application-page__error" role="alert">{error}</div>}

        <form className="coach-application-form" onSubmit={handleSubmit} noValidate>
          <label className="coach-application-field" htmlFor="coach-app-name">
            <span>Ad Soyad:</span>
            <input id="coach-app-name" type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} required disabled={loading} />
          </label>
          <div className="coach-application-field-group">
            <label className="coach-application-field" htmlFor="coach-app-email">
              <span>E-posta:</span>
              <input ref={emailInputRef} id="coach-app-email" type="email" inputMode="email" autoComplete="email" value={email} onChange={(e) => { setEmail(e.target.value); if (emailError) setEmailError(null); }} aria-invalid={emailError ? true : undefined} aria-describedby={emailError ? "coach-app-email-error" : undefined} required disabled={loading} />
            </label>
            {emailError && <span className="coach-application-field__error" id="coach-app-email-error" role="alert">{emailError}</span>}
          </div>
          <label className="coach-application-field" htmlFor="coach-app-phone">
            <span>Telefon (isteğe bağlı):</span>
            <input id="coach-app-phone" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} disabled={loading} />
          </label>
          <label className="coach-application-field" htmlFor="coach-app-experience">
            <span>Deneyimin (üniversite, bölüm, YKS'ye dair deneyimin vb.):</span>
            <textarea id="coach-app-experience" rows={5} value={experience} onChange={(e) => setExperience(e.target.value)} disabled={loading} />
          </label>
          <button className="coach-application-submit" type="submit" disabled={loading}>
            {loading ? "Gönderiliyor…" : "Başvuruyu Gönder"}
          </button>
        </form>
      </div>
    </div>
  );
};
