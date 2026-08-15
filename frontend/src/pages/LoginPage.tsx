import React, { useState, useEffect } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";
import { getApiBaseUrl } from "../api/httpClient";
import { ApiError } from "../api/ApiError";
import { readinessPathForUser } from "../auth/authNavigation";
import "./auth-page.css";

export const LoginPage: React.FC = () => {
  const { login, isAuthenticated, user, isSuspended } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<ApiError | Error | string | null>(null);
  const [loading, setLoading] = useState(false);

  // Redirect users who are already logged in
  useEffect(() => {
    if (isSuspended) {
      navigate("/suspended");
    } else if (isAuthenticated && user) {
      navigate(readinessPathForUser(user));
    }
  }, [isAuthenticated, user, isSuspended, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email.trim() || !password.trim()) {
      setError("E-posta ve şifre gereklidir.");
      return;
    }

    setLoading(true);
    try {
      await login(email, password);
      // Navigation is handled automatically by the useEffect redirect guard
    } catch (err: unknown) {
      setError(err instanceof Error ? err : "Giriş yapılamadı.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="auth-eyebrow">
          <span aria-hidden="true" />
          YKS MENTÖRLÜK PROGRAMI
        </div>
        <h1>Tekrar hoş geldin</h1>
        <p className="auth-lead">Hesabına giriş yap, hedefine kaldığın yerden devam et.</p>

        {searchParams.get("accountDeleted") === "1" && (
          <div role="status" className="auth-notice auth-notice--info">
            Hesabınızla ilgili silme işlemi tamamlandı. Oturumunuz güvenli biçimde kapatıldı.
          </div>
        )}
        {searchParams.get("passwordChanged") === "1" && (
          <div role="status" className="auth-notice auth-notice--success">
            Şifreniz değiştirildi ve tüm oturumlar kapatıldı. Yeni şifrenizle giriş yapın.
          </div>
        )}

        <FormError error={error} />

        <form onSubmit={handleSubmit}>
          <div className="auth-field">
            <label htmlFor="login-email">E-posta</label>
            <input
              id="login-email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              disabled={loading}
            />
          </div>
          <div className="auth-field">
            <label htmlFor="login-password">Şifre</label>
            <input
              id="login-password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              disabled={loading}
            />
            <Link className="auth-forgot" to="/forgot-password">
              Şifremi unuttum
            </Link>
          </div>
          <button className="auth-submit" type="submit" disabled={loading}>
            {loading ? "Giriş Yapılıyor…" : "Giriş Yap"}
          </button>
        </form>

        <div className="auth-divider">veya</div>

        <a className="auth-google" href={`${getApiBaseUrl()}/oauth2/authorization/google`}>
          <svg aria-hidden="true" width="18" height="18" viewBox="0 0 18 18">
            <path fill="#4285F4" d="M17.64 9.2c0-.64-.06-1.25-.16-1.84H9v3.48h4.84a4.14 4.14 0 0 1-1.8 2.72v2.26h2.9c1.7-1.57 2.7-3.88 2.7-6.62Z" />
            <path fill="#34A853" d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.9-2.26c-.81.54-1.84.86-3.06.86-2.35 0-4.34-1.58-5.05-3.71H.95v2.33A9 9 0 0 0 9 18Z" />
            <path fill="#FBBC05" d="M3.95 10.71A5.4 5.4 0 0 1 3.67 9c0-.59.1-1.17.28-1.71V4.96H.95A9 9 0 0 0 0 9c0 1.45.35 2.83.95 4.04l3-2.33Z" />
            <path fill="#EA4335" d="M9 3.58c1.32 0 2.51.45 3.44 1.35l2.58-2.58C13.46.89 11.43 0 9 0A9 9 0 0 0 .95 4.96l3 2.33C4.66 5.16 6.65 3.58 9 3.58Z" />
          </svg>
          Google ile devam et
        </a>

        <p className="auth-footer">
          Hesabınız yok mu? <Link to="/register">Kayıt Ol</Link>
        </p>
      </div>
    </div>
  );
};
