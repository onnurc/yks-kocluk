import React, { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { readinessPathForUser } from "../auth/authNavigation";
import { FormError } from "../components/FormError";

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

  return (
    <main style={{ maxWidth: "460px", margin: "5rem auto", padding: "2rem", textAlign: "center" }}>
      <h1>Google ile giriş</h1>
      <FormError error={error} />
      {!error && <p role="status">Giriş tamamlanıyor…</p>}
      {error && <button type="button" onClick={() => navigate("/login", { replace: true })}>Girişe dön</button>}
    </main>
  );
};
