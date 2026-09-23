import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { readinessPathForUser } from "../auth/authNavigation";
import { FormError } from "../components/FormError";
import { BrandLogo } from "../public/BrandLogo";
import { AuthPageShell } from "./AuthPageShell";
import "./auth-page.css";
import "./oauth-callback-page.css";

export const OAuthCallbackPage: React.FC = () => {
  const { completeOAuthLogin } = useAuth();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const started = useRef(false);
  const code = searchParams.get("code");
  const [error, setError] = useState<Error | string | null>(code ? null : "Google giriş kodu bulunamadı. Lütfen yeniden giriş yapın.");

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    if (!code) return;

    void completeOAuthLogin(code)
      .then((user) => {
        navigate(readinessPathForUser(user), { replace: true });
      })
      .catch((cause) => setError(cause instanceof Error ? cause : "Google ile giriş tamamlanamadı."));
  }, [code, completeOAuthLogin, navigate]);

  if (!error) {
    return (
      <div className="auth-shell oauth-callback-shell" aria-busy="true">
        <header className="auth-shell__brand">
          <BrandLogo size="compact" />
        </header>
        <main className="oauth-callback-transition" aria-busy="true">
          <span role="status" aria-label="Oturum hazırlanıyor." />
        </main>
      </div>
    );
  }

  return (
    <AuthPageShell title="Google ile giriş" compact>
      <div className="auth-error-slot" role="alert">
        <FormError error={error} />
      </div>
      <button className="auth-submit" type="button" onClick={() => navigate("/login", { replace: true })}>Girişe dön</button>
    </AuthPageShell>
  );
};
