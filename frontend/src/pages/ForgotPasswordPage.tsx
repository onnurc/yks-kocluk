import React, { useState } from "react";
import { Link } from "react-router-dom";
import { authApi } from "../auth/authApi";
import { FormError } from "../components/FormError";

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

  return <main style={{ maxWidth: 440, margin: "4rem auto", padding: "2rem", background: "white", border: "1px solid #cbd5e1", borderRadius: 8 }}>
    <h1>Parolamı Unuttum</h1>
    <p>E-posta adresinizi girin. Hesabınız uygunsa size tek kullanımlık bir bağlantı göndereceğiz.</p>
    {message ? <div role="status" style={{ padding: ".75rem", background: "#ecfdf5", color: "#166534" }}>{message}</div> : <>
      <FormError error={error} />
      <form onSubmit={submit}>
        <label htmlFor="forgot-email">E-posta</label>
        <input id="forgot-email" type="email" autoComplete="email" required value={email} onChange={e => setEmail(e.target.value)} disabled={loading} style={{ width: "100%", padding: ".65rem", boxSizing: "border-box", margin: ".5rem 0 1rem" }} />
        <button disabled={loading} type="submit">{loading ? "Gönderiliyor…" : "Sıfırlama bağlantısı gönder"}</button>
      </form>
    </>}
    <p><Link to="/login">Giriş sayfasına dön</Link></p>
  </main>;
};
