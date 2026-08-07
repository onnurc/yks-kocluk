import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { authApi } from "../auth/authApi";
import { readinessPathForUser } from "../auth/authNavigation";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";

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
    try {
      const response = await authApi.resendVerification();
      setCooldown(secondsUntil(response.nextResendAt) || 60);
      setCode("");
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
    <main style={{ maxWidth: "440px", margin: "4rem auto", padding: "2rem", border: "1px solid #cbd5e1", borderRadius: "8px", background: "white" }}>
      <h1 style={{ fontSize: "1.6rem" }}>E-posta adresinizi doğrulayın</h1>
      <p>E-posta adresinize gönderdiğimiz 6 haneli doğrulama kodunu girin. Kod 10 dakika geçerlidir.</p>
      <FormError error={error} />
      {success && <p role="status" style={{ color: "#166534" }}>E-posta adresiniz doğrulandı. Yönlendiriliyorsunuz…</p>}
      <form onSubmit={verify}>
        <label htmlFor="verification-code" style={{ display: "block", fontWeight: 700, marginBottom: ".5rem" }}>6 haneli kod</label>
        <input id="verification-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
          value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, "").slice(0, 6))}
          disabled={loading || success} style={{ width: "100%", boxSizing: "border-box", padding: ".75rem", letterSpacing: ".35rem", fontSize: "1.25rem" }} />
        <button type="submit" disabled={loading || success || code.length !== 6} style={{ width: "100%", marginTop: "1rem", padding: ".75rem" }}>
          {loading ? "Doğrulanıyor…" : "E-postayı doğrula"}
        </button>
      </form>
      <button type="button" onClick={() => void resend()} disabled={resending || cooldown > 0 || success} style={{ marginTop: "1rem" }}>
        {resending ? "Gönderiliyor…" : cooldown > 0 ? `Yeni kod için ${cooldown} sn` : "Yeni kod gönder"}
      </button>
    </main>
  );
};
