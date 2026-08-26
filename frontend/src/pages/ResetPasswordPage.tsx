import React, { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { authApi } from "../auth/authApi";
import { ApiError } from "../api/ApiError";
import { FormError } from "../components/FormError";
import { AuthPageShell } from "./AuthPageShell";
import "./auth-page.css";

const MIN_PASSWORD_LENGTH = 8;
const MAX_PASSWORD_LENGTH = 72;

type ValidationErrors = {
  password?: string;
  confirmation?: string;
};

export const ResetPasswordPage: React.FC = () => {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const [password, setPassword] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [loading, setLoading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<Error | string | null>(
    token ? null : "Parola sıfırlama bağlantısı geçersiz veya eksik.",
  );
  const [validationErrors, setValidationErrors] = useState<ValidationErrors>({});

  const validate = (): boolean => {
    const nextErrors: ValidationErrors = {};
    if (password.length < MIN_PASSWORD_LENGTH || password.length > MAX_PASSWORD_LENGTH) {
      nextErrors.password = "Parola 8–72 karakter arasında olmalıdır.";
    }
    if (password !== confirmation) {
      nextErrors.confirmation = "Parola tekrarı yeni parolayla eşleşmiyor.";
    }
    setValidationErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    if (!token || !validate()) return;

    setLoading(true);
    try {
      await authApi.resetPassword(token, password);
      setSuccess(true);
    } catch (caught) {
      if (caught instanceof ApiError && caught.code === "PASSWORD_RESET_TOKEN_INVALID") {
        setError("Parola sıfırlama bağlantısı geçersiz veya süresi dolmuş.");
      } else {
        setError(caught instanceof Error ? caught : "Parola yenilenemedi.");
      }
    } finally {
      setLoading(false);
    }
  };

  if (success) {
    return (
      <AuthPageShell
        title="Parolanız Yenilendi"
        lead="Yeni parolanız kaydedildi. Uniform hesabınıza güvenle giriş yapabilirsiniz."
        icon="✓"
      >
        <div className="auth-notice auth-notice--success" role="status">
          Parolanız başarıyla yenilendi. Yeni parolanızla giriş yapabilirsiniz.
        </div>
        <Link className="auth-submit auth-submit--link" to="/login">Giriş Yap</Link>
      </AuthPageShell>
    );
  }

  return (
    <AuthPageShell
      title="Yeni Parola Belirle"
      lead="Hesabınızı korumak için 8–72 karakter arasında yeni bir parola oluşturun."
      icon="✦"
    >
      <div className="auth-error-slot"><FormError error={error} /></div>
      <form className="auth-form" onSubmit={submit} noValidate>
        <p className="auth-form-hint" id="reset-password-requirements">
          Parolanız 8–72 karakter arasında olmalıdır.
        </p>

        <div className="auth-field-group">
          <label className="auth-field" htmlFor="new-password">
            <span>Yeni Parola</span>
            <input
              id="new-password"
              type="password"
              autoComplete="new-password"
              required
              maxLength={MAX_PASSWORD_LENGTH}
              value={password}
              onChange={(event) => {
                setPassword(event.target.value);
                setValidationErrors((current) => ({ ...current, password: undefined }));
              }}
              disabled={!token || loading}
              aria-invalid={Boolean(validationErrors.password)}
              aria-describedby={`reset-password-requirements${validationErrors.password ? " reset-password-error" : ""}`}
            />
          </label>
          {validationErrors.password && <p className="auth-field-error" id="reset-password-error" role="alert">{validationErrors.password}</p>}
        </div>

        <div className="auth-field-group">
          <label className="auth-field" htmlFor="confirm-password">
            <span>Yeni Parola Tekrar</span>
            <input
              id="confirm-password"
              type="password"
              autoComplete="new-password"
              required
              maxLength={MAX_PASSWORD_LENGTH}
              value={confirmation}
              onChange={(event) => {
                setConfirmation(event.target.value);
                setValidationErrors((current) => ({ ...current, confirmation: undefined }));
              }}
              disabled={!token || loading}
              aria-invalid={Boolean(validationErrors.confirmation)}
              aria-describedby={validationErrors.confirmation ? "confirm-password-error" : undefined}
            />
          </label>
          {validationErrors.confirmation && <p className="auth-field-error" id="confirm-password-error" role="alert">{validationErrors.confirmation}</p>}
        </div>

        <button className="auth-submit" type="submit" disabled={!token || loading}>
          {loading ? "Yenileniyor…" : "Parolayı Yenile"}
        </button>
      </form>
      <p className="auth-footer auth-footer--return"><Link to="/login">Giriş sayfasına dön</Link></p>
      <p className="auth-security"><span aria-hidden="true">♙</span> Sıfırlama bağlantınız ve parola bilgileriniz güvenli biçimde işlenir.</p>
    </AuthPageShell>
  );
};
