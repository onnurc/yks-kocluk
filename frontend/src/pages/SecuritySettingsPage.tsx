import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import { authApi } from "../auth/authApi";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";

export const SecuritySettingsPage: React.FC = () => {
  const { user, clearSession } = useAuth(); const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState(""); const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState(""); const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | string | null>(null);
  if (!user?.hasLocalPassword) return <main style={{ maxWidth: 680, margin: "2rem auto" }}><h1>Güvenlik</h1><p>Bu hesap Google ile giriş kullanıyor ve yerel bir şifresi bulunmuyor.</p></main>;
  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setError(null);
    if (newPassword !== confirmation) { setError("Yeni şifreler eşleşmiyor."); return; }
    setLoading(true);
    try { await authApi.changePassword(currentPassword, newPassword); clearSession(); navigate("/login?passwordChanged=1", { replace: true }); }
    catch (err) { setError(err instanceof Error ? err : "Şifre değiştirilemedi."); }
    finally { setLoading(false); }
  };
  return <main style={{ maxWidth: 680, margin: "2rem auto", padding: "1.5rem" }}><h1>Ayarlar → Güvenlik → Şifre Değiştir</h1><p>Şifre değişikliği tüm oturumları kapatır.</p><FormError error={error} /><form onSubmit={submit}>
    <label>Mevcut şifre<input type="password" autoComplete="current-password" required value={currentPassword} onChange={e => setCurrentPassword(e.target.value)} /></label><br />
    <label>Yeni şifre<input type="password" autoComplete="new-password" minLength={8} maxLength={72} required value={newPassword} onChange={e => setNewPassword(e.target.value)} /></label><br />
    <label>Yeni şifre tekrar<input type="password" autoComplete="new-password" required value={confirmation} onChange={e => setConfirmation(e.target.value)} /></label><br />
    <button disabled={loading} type="submit">{loading ? "Değiştiriliyor…" : "Şifreyi değiştir"}</button>
  </form></main>;
};
