import React, { useState } from "react";
import { coachApplicationApi } from "./coachApplicationApi";
import { ApiError } from "../api/ApiError";
import "./coach-application-page.css";

export const CoachApplicationPage: React.FC = () => {
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [experience, setExperience] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!fullName.trim() || !email.trim()) {
      setError("Ad Soyad ve e-posta alanları gereklidir.");
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
      setError(err instanceof ApiError ? err.detail : "Başvuru gönderilemedi. Lütfen daha sonra tekrar deneyin.");
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

        <form className="coach-application-form" onSubmit={handleSubmit}>
          <label className="coach-application-field" htmlFor="coach-app-name">
            <span>Ad Soyad:</span>
            <input id="coach-app-name" type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} required disabled={loading} />
          </label>
          <label className="coach-application-field" htmlFor="coach-app-email">
            <span>E-posta:</span>
            <input id="coach-app-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required disabled={loading} />
          </label>
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
