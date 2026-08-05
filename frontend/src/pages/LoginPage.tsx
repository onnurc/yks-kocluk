import React, { useState, useEffect } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";
import { getApiBaseUrl } from "../api/httpClient";
import { ApiError } from "../api/ApiError";

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
      if (!user.legalOnboardingCompleted) {
        navigate("/legal-onboarding");
      } else if (user.role === "ADMIN") {
        navigate("/admin");
      } else {
        navigate("/dashboard");
      }
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
    <div style={{ maxWidth: "400px", margin: "4rem auto", padding: "2rem", border: "1px solid #ccc", borderRadius: "8px", backgroundColor: "#fff" }}>
      <h2 style={{ marginTop: 0, marginBottom: "1.5rem" }}>Giriş Yap</h2>

      {searchParams.get("accountDeleted") === "1" && (
        <div role="status" style={{ padding: ".75rem", marginBottom: "1rem", background: "#e2e8f0", color: "#334155", borderRadius: "4px" }}>
          Hesabınızla ilgili silme işlemi tamamlandı. Oturumunuz güvenli biçimde kapatıldı.
        </div>
      )}

      <FormError error={error} />

      <form onSubmit={handleSubmit}>
        <div style={{ marginBottom: "1rem" }}>
          <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>E-posta:</label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            disabled={loading}
            style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
          />
        </div>
        <div style={{ marginBottom: "1.5rem" }}>
          <label style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Şifre:</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            disabled={loading}
            style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
          />
        </div>
        <button
          type="submit"
          disabled={loading}
          style={{ width: "100%", padding: "0.75rem", backgroundColor: "#007bff", color: "white", border: "none", borderRadius: "4px", cursor: loading ? "not-allowed" : "pointer", fontSize: "1rem" }}
        >
          {loading ? "Giriş Yapılıyor..." : "Giriş Yap"}
        </button>
      </form>
      <div style={{ display: "flex", alignItems: "center", gap: ".75rem", margin: "1.25rem 0", color: "#64748b" }}><hr style={{ flex: 1 }} /><span>veya</span><hr style={{ flex: 1 }} /></div>
      <a href={`${getApiBaseUrl()}/oauth2/authorization/google`} style={{ display: "block", textAlign: "center", padding: ".75rem", border: "1px solid #94a3b8", borderRadius: "4px", color: "#1e293b", textDecoration: "none", fontWeight: 600 }}>
        Google ile devam et
      </a>
      <p style={{ marginTop: "1.5rem", textAlign: "center", marginBottom: 0 }}>
        Hesabınız yok mu? <Link to="/register">Kayıt Ol</Link>
      </p>
    </div>
  );
};
