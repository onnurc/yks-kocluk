import React, { useState } from "react";
import { Link } from "react-router-dom";
import { authApi } from "../auth/authApi";
import { FormError } from "../components/FormError";
import { AuthPageShell } from "./AuthPageShell";
import "./auth-page.css";

export const ForgotPasswordPage: React.FC = () => {
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState<Error | string | null>(null);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setLoading(true); setError(null);
    try { setMessage((await authApi.forgotPassword(email)).message); }
    catch (err) { setError(err instanceof Error ? err : "İstek tamamlanamadı. Lütfen tekrar deneyin."); }
    finally { setLoading(false); }
  };

  return (
    <AuthPageShell
      title="Parolamı Unuttum"
      lead="E-posta adresinizi girin. Hesabınız uygunsa mevcut güvenli sıfırlama bağlantısını size göndereceğiz."
      icon="✦"
    >
      {message ? (
        <div className="auth-notice auth-notice--success" role="status">{message}</div>
      ) : (
        <>
          <div className="auth-error-slot"><FormError error={error} /></div>
          <form className="auth-form" onSubmit={submit}>
            <label className="auth-field" htmlFor="forgot-email">
              <span>E-posta Adresi</span>
              <input id="forgot-email" type="email" autoComplete="email" required value={email} onChange={(event) => setEmail(event.target.value)} disabled={loading} />
            </label>
            <button className="auth-submit" disabled={loading} type="submit">
              {loading ? "Gönderiliyor…" : "Sıfırlama Bağlantısı Gönder"}
            </button>
          </form>
        </>
      )}
      <p className="auth-footer auth-footer--return"><Link to="/login">Giriş sayfasına dön</Link></p>
      <p className="auth-security"><span aria-hidden="true">♙</span> Güvenliğiniz için hesap durumu bu ekranda paylaşılmaz.</p>
    </AuthPageShell>
  );
};
