import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { authApi } from "../auth/authApi";
import { readinessPathForUser } from "../auth/authNavigation";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";
import { AuthPageShell } from "./AuthPageShell";
import "./auth-page.css";

const secondsUntil = (value?: string): number => value
  ? Math.max(0, Math.ceil((new Date(value).getTime() - Date.now()) / 1000))
  : 0;

export const VerifyEmailPage: React.FC = () => {
  const { user, refreshCurrentUser } = useAuth();
  const navigate = useNavigate();
  const [code, setCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState<ApiError | Error | string | null>(null);
  const [success, setSuccess] = useState(false);
  const [resendNotice, setResendNotice] = useState(false);
  const [cooldown, setCooldown] = useState(0);

  useEffect(() => {
    if (user?.emailVerified) navigate(readinessPathForUser(user), { replace: true });
  }, [navigate, user]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setInterval(() => setCooldown((current) => Math.max(0, current - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [cooldown]);

  const verify = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!/^\d{6}$/.test(code)) {
      setError("Lütfen 6 haneli doğrulama kodunu girin.");
      return;
    }
    setLoading(true);
    setError(null);
    setResendNotice(false);
    try {
      await authApi.verifyEmail(code);
      setSuccess(true);
      const refreshed = await refreshCurrentUser();
      if (refreshed) navigate(readinessPathForUser(refreshed), { replace: true });
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === "EMAIL_VERIFICATION_CODE_EXPIRED") {
        setError("Doğrulama kodunun süresi dolmuş. Yeni bir kod isteyin.");
      } else if (cause instanceof ApiError && cause.code === "EMAIL_VERIFICATION_CODE_INVALID") {
        setError("Girdiğiniz doğrulama kodu geçersiz.");
      } else {
        setError(cause instanceof Error ? cause : "E-posta doğrulanamadı.");
      }
    } finally {
      setLoading(false);
    }
  };

  const resend = async () => {
    setResending(true);
    setError(null);
    setResendNotice(false);
    try {
      const response = await authApi.resendVerification();
      setCooldown(secondsUntil(response.nextResendAt) || 60);
      setCode("");
      setResendNotice(true);
    } catch (cause) {
      if (cause instanceof ApiError && cause.code === "EMAIL_VERIFICATION_RESEND_TOO_SOON") {
        setCooldown(secondsUntil(cause.nextAllowedAt));
        setError("Yeni kod istemeden önce geri sayımın bitmesini bekleyin.");
      } else {
        setError(cause instanceof Error ? cause : "Yeni kod gönderilemedi.");
      }
    } finally {
      setResending(false);
    }
  };

  return (
    <AuthPageShell
      title="E-posta Adresini Doğrula"
      lead="E-posta adresine gönderdiğimiz 6 haneli kodu gir. Kod 10 dakika boyunca geçerlidir."
      icon="✉"
    >
      <div className="auth-error-slot" id="verification-error" aria-live="polite"><FormError error={error} /></div>
      {success && <p className="auth-notice auth-notice--success" role="status">E-posta adresiniz doğrulandı. Yönlendiriliyorsunuz…</p>}
      {resendNotice && !success && <p className="auth-notice auth-notice--success" role="status">Yeni doğrulama kodu e-posta adresinize gönderildi.</p>}
      <form className="auth-form" onSubmit={verify} noValidate>
        <label className="auth-field auth-field--verification" htmlFor="verification-code">
          <span>6 haneli doğrulama kodu:</span>
          <input
            id="verification-code"
            inputMode="numeric"
            pattern="[0-9]{6}"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
            disabled={loading || success}
            aria-invalid={error ? true : undefined}
            aria-describedby="verification-code-hint verification-error"
            required
          />
        </label>
        <p className="auth-form-hint" id="verification-code-hint">Kod ulaşmadıysa geri sayım tamamlandığında yeni bir kod isteyebilirsin.</p>
        <button className="auth-submit" type="submit" disabled={loading || success || code.length !== 6}>
          {loading ? "Doğrulanıyor…" : "E-postayı Doğrula"}
        </button>
      </form>
      <button className="auth-secondary" type="button" onClick={() => void resend()} disabled={resending || cooldown > 0 || success}>
        {resending ? "Gönderiliyor…" : cooldown > 0 ? `Yeni kod için ${cooldown} sn` : "Yeni Kod Gönder"}
      </button>
      <p className="auth-security"><span aria-hidden="true">◇</span> Kodunuzu kimseyle paylaşmayın.</p>
    </AuthPageShell>
  );
};
