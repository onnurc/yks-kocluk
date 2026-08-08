import React, { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { authApi } from "../auth/authApi";
import { ApiError } from "../api/ApiError";
import { FormError } from "../components/FormError";

export const ResetPasswordPage: React.FC = () => {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<Error | string | null>(token ? null : "Şifre sıfırlama bağlantısı geçersiz veya eksik.");

  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setError(null);
    if (password !== confirmation) { setError("Şifreler eşleşmiyor."); return; }
    setLoading(true);
    try { await authApi.resetPassword(token, password); setSuccess(true); }
    catch (err) {
      if (err instanceof ApiError && err.code === "PASSWORD_RESET_TOKEN_INVALID") setError("Şifre sıfırlama bağlantısı geçersiz veya süresi dolmuş.");
      else setError(err instanceof Error ? err : "Şifre yenilenemedi.");
    } finally { setLoading(false); }
  };

  if (success) return <main style={{ maxWidth: 440, margin: "4rem auto" }}><h1>Şifreniz yenilendi</h1><p role="status">Şifreniz başarıyla yenilendi. Yeni şifrenizle giriş yapabilirsiniz.</p><Link to="/login">Giriş yap</Link></main>;
  return <main style={{ maxWidth: 440, margin: "4rem auto", padding: "2rem", background: "white" }}>
    <h1>Yeni şifre belirle</h1><p>Şifreniz 8–72 karakter arasında olmalıdır.</p><FormError error={error} />
    <form onSubmit={submit}>
      <label htmlFor="new-password">Yeni şifre</label><input id="new-password" type="password" autoComplete="new-password" minLength={8} maxLength={72} required value={password} onChange={e => setPassword(e.target.value)} disabled={!token || loading} style={{ display: "block", width: "100%", margin: ".5rem 0 1rem" }} />
      <label htmlFor="confirm-password">Yeni şifre tekrar</label><input id="confirm-password" type="password" autoComplete="new-password" required value={confirmation} onChange={e => setConfirmation(e.target.value)} disabled={!token || loading} style={{ display: "block", width: "100%", margin: ".5rem 0 1rem" }} />
      <button type="submit" disabled={!token || loading}>{loading ? "Yenileniyor…" : "Şifreyi yenile"}</button>
    </form><p><Link to="/login">Giriş sayfasına dön</Link></p>
  </main>;
};
