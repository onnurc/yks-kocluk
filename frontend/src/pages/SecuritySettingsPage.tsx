import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { authApi } from "../auth/authApi";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";
import "./security-settings.css";
import { MAX_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH } from "../auth/passwordPolicy";

function LockIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="5" y="10" width="14" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" /></svg>;
}

export const SecuritySettingsPage = () => {
  const { user, clearSession } = useAuth();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<Error | string | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    if (newPassword !== confirmation) {
      setError("Yeni şifreler eşleşmiyor.");
      return;
    }
    setLoading(true);
    try {
      await authApi.changePassword(currentPassword, newPassword);
      clearSession();
      navigate("/login?passwordChanged=1", { replace: true });
    } catch (caught) {
      setError(caught instanceof Error ? caught : "Şifre değiştirilemedi. Lütfen yeniden deneyin.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="security-settings" aria-labelledby="security-settings-title">
      <header className="security-settings__header">
        <p className="security-settings__eyebrow">Ayarlar</p>
        <h1 id="security-settings-title">Şifre Değiştir</h1>
        <p>Yeni şifreniz kaydedildiğinde güvenliğiniz için açık oturumlarınız kapatılır.</p>
      </header>

      {!user?.hasLocalPassword ? (
        <div className="security-settings__unavailable">
          <span className="security-settings__icon"><LockIcon /></span>
          <h2>Yerel şifre kullanılmıyor</h2>
          <p>Bu hesap Google ile giriş kullanıyor ve yerel bir şifresi bulunmuyor.</p>
        </div>
      ) : (
        <div className="security-settings__card">
          <div className="security-settings__card-heading">
            <span className="security-settings__icon"><LockIcon /></span>
            <div><h2>Hesap şifresi</h2><p>Şifreniz 12–72 karakter arasında olmalıdır.</p></div>
          </div>

          <div className="security-settings__error"><FormError error={error} /></div>

          <form className="security-settings__form" onSubmit={submit}>
            <label htmlFor="current-password">
              <span>Mevcut şifre</span>
              <input id="current-password" type="password" autoComplete="current-password" required value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} disabled={loading} />
            </label>
            <label htmlFor="new-password">
              <span>Yeni şifre</span>
              <input id="new-password" type="password" autoComplete="new-password" minLength={MIN_PASSWORD_LENGTH} maxLength={MAX_PASSWORD_LENGTH} required value={newPassword} onChange={(event) => setNewPassword(event.target.value)} disabled={loading} />
            </label>
            <label htmlFor="new-password-confirmation">
              <span>Yeni şifre tekrar</span>
              <input id="new-password-confirmation" type="password" autoComplete="new-password" minLength={MIN_PASSWORD_LENGTH} maxLength={MAX_PASSWORD_LENGTH} required value={confirmation} onChange={(event) => setConfirmation(event.target.value)} disabled={loading} />
            </label>
            <button type="submit" disabled={loading}>{loading ? "Değiştiriliyor…" : "Şifreyi Değiştir"}</button>
          </form>

          <p className="security-settings__note"><span aria-hidden="true">✦</span> Değişiklikten sonra yeni şifrenizle tekrar giriş yapmanız gerekir.</p>
        </div>
      )}
    </section>
  );
};
