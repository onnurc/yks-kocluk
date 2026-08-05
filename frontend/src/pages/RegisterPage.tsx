import React, { useState, useEffect } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";
import { ApiError } from "../api/ApiError";
import { LegalDocumentViewer } from "../legal/LegalDocumentViewer";
import { useLegalDocuments } from "../legal/useLegalDocuments";
import { isStaleLegalDocumentError, legalErrorMessage } from "../legal/legalErrors";

const REGISTRATION_DOCUMENT_TYPES = ["TERMS_OF_USE", "EXPLICIT_CONSENT", "KVKK_NOTICE"] as const;

export const RegisterPage: React.FC = () => {
  const { register, isAuthenticated, user, isSuspended } = useAuth();
  const navigate = useNavigate();

  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"STUDENT" | "COACH">("STUDENT");
  const [dateOfBirth, setDateOfBirth] = useState("");
  const [error, setError] = useState<ApiError | Error | string | null>(null);
  const [loading, setLoading] = useState(false);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [explicitConsentAccepted, setExplicitConsentAccepted] = useState(false);
  const [marketingEmailOptIn, setMarketingEmailOptIn] = useState(false);
  const [marketingSmsOptIn, setMarketingSmsOptIn] = useState(false);
  const legalDocuments = useLegalDocuments([...REGISTRATION_DOCUMENT_TYPES]);

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

    if (!firstName.trim() || !lastName.trim()) {
      setError("Ad ve Soyad alanları gereklidir.");
      return;
    }
    if (!email.trim() || !password.trim()) {
      setError("E-posta ve şifre gereklidir.");
      return;
    }
    if (password.length < 8) {
      setError("Şifre en az 8 karakter olmalıdır.");
      return;
    }
    if (role === "STUDENT") {
      if (!dateOfBirth) {
        setError("Öğrenci kaydı için doğum tarihi zorunludur.");
        return;
      }
      const birthDate = new Date(dateOfBirth);
      const today = new Date();
      if (birthDate > today) {
        setError("Doğum tarihi gelecekte olamaz.");
        return;
      }
    }

    if (!legalDocuments.ready) {
      setError("Hukuki metinler yüklenemedi. Lütfen yeniden deneyin.");
      return;
    }
    if (!termsAccepted || !explicitConsentAccepted) {
      setError("Kayıt için gerekli hukuki metinleri onaylamalısınız.");
      return;
    }

    setLoading(true);
    try {
      const fullName = `${firstName.trim()} ${lastName.trim()}`;
      await register({
        email,
        password,
        fullName,
        role,
        dateOfBirth: role === "STUDENT" ? dateOfBirth : undefined,
        acceptedTermsDocumentId: legalDocuments.documents.TERMS_OF_USE!.id,
        acceptedExplicitConsentDocumentId: legalDocuments.documents.EXPLICIT_CONSENT!.id,
        marketingEmailOptIn,
        marketingSmsOptIn,
      });
      // Success auto-login redirects via useEffect
    } catch (err) {
      const message = legalErrorMessage(err);
      setError(message || (err as ApiError | Error));
      if (isStaleLegalDocumentError(err)) {
        setTermsAccepted(false);
        setExplicitConsentAccepted(false);
        await legalDocuments.reload();
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: "560px", margin: "4rem auto", padding: "2rem", border: "1px solid #ccc", borderRadius: "8px", backgroundColor: "#fff" }}>
      <h2 style={{ marginTop: 0, marginBottom: "1.5rem" }}>Kayıt Ol</h2>

      <FormError error={error} />

      <form onSubmit={handleSubmit}>
        <div style={{ display: "flex", gap: "1rem", marginBottom: "1rem" }}>
          <div style={{ flex: 1 }}>
            <label htmlFor="first-name" style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Ad:</label>
            <input
              id="first-name"
              type="text"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              required
              disabled={loading}
              style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
            />
          </div>
          <div style={{ flex: 1 }}>
            <label htmlFor="last-name" style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Soyad:</label>
            <input
              id="last-name"
              type="text"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              required
              disabled={loading}
              style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
            />
          </div>
        </div>
        <div style={{ marginBottom: "1rem" }}>
          <label htmlFor="register-email" style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>E-posta:</label>
          <input
            id="register-email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            disabled={loading}
            style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
          />
        </div>
        <div style={{ marginBottom: "1rem" }}>
          <label htmlFor="register-password" style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Şifre (Min 8 karakter):</label>
          <input
            id="register-password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            disabled={loading}
            style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
          />
        </div>
        {role === "STUDENT" && (
          <div style={{ marginBottom: "1rem" }}>
            <label htmlFor="date-of-birth" style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Doğum Tarihi:</label>
            <input
              id="date-of-birth"
              type="date"
              value={dateOfBirth}
              onChange={(e) => setDateOfBirth(e.target.value)}
              required
              disabled={loading}
              style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc" }}
            />
          </div>
        )}
        <div style={{ marginBottom: "1.5rem" }}>
          <label htmlFor="register-role" style={{ display: "block", marginBottom: "0.5rem", fontWeight: "bold" }}>Rol Seçimi:</label>
          <select
            id="register-role"
            value={role}
            onChange={(e) => setRole(e.target.value as "STUDENT" | "COACH")}
            disabled={loading}
            style={{ width: "100%", padding: "0.5rem", boxSizing: "border-box", borderRadius: "4px", border: "1px solid #ccc", backgroundColor: "#fff" }}
          >
            <option value="STUDENT">Öğrenci</option>
            <option value="COACH">Koç</option>
          </select>
        </div>
        <fieldset style={{ margin: "0 0 1.5rem", padding: "1rem", border: "1px solid #cbd5e1", borderRadius: "8px" }}>
          <legend style={{ fontWeight: 700 }}>Hukuki onaylar</legend>
          {legalDocuments.loading && <p role="status" style={{ color: "#475569" }}>Hukuki metinler yükleniyor…</p>}
          {legalDocuments.error && (
            <div role="alert" style={{ color: "#b91c1c", marginBottom: "0.75rem" }}>
              Hukuki metinler yüklenemedi. Lütfen yeniden deneyin.{" "}
              <button type="button" onClick={() => void legalDocuments.reload()}>Yeniden Dene</button>
            </div>
          )}
          <p style={{ fontSize: "0.9rem" }}>
            Kişisel verilerinizin işlenmesine ilişkin{" "}
            <LegalDocumentViewer
              label="KVKK Aydınlatma Metni"
              document={legalDocuments.documents.KVKK_NOTICE}
              loading={legalDocuments.loading}
              error={legalDocuments.error}
              onRetry={() => void legalDocuments.reload()}
            />
            ’ni inceleyebilirsiniz.
          </p>
          <div style={{ display: "flex", gap: "0.6rem", alignItems: "flex-start", marginBottom: "0.75rem" }}>
            <input id="accept-terms" type="checkbox" checked={termsAccepted} onChange={(event) => setTermsAccepted(event.target.checked)} disabled={loading || !legalDocuments.ready} />
            <div><label htmlFor="accept-terms">Kullanım Koşulları’nı okudum ve kabul ediyorum. <strong>(Zorunlu)</strong></label>{" "}<LegalDocumentViewer label="Metni görüntüle" document={legalDocuments.documents.TERMS_OF_USE} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} /></div>
          </div>
          <div style={{ display: "flex", gap: "0.6rem", alignItems: "flex-start", marginBottom: "0.75rem" }}>
            <input id="accept-explicit-consent" type="checkbox" checked={explicitConsentAccepted} onChange={(event) => setExplicitConsentAccepted(event.target.checked)} disabled={loading || !legalDocuments.ready} />
            <div><label htmlFor="accept-explicit-consent">Açık Rıza Metni’ni okudum ve kabul ediyorum. <strong>(Zorunlu)</strong></label>{" "}<LegalDocumentViewer label="Metni görüntüle" document={legalDocuments.documents.EXPLICIT_CONSENT} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} /></div>
          </div>
          <label style={{ display: "flex", gap: "0.6rem", marginBottom: "0.75rem" }}>
            <input type="checkbox" checked={marketingEmailOptIn} onChange={(event) => setMarketingEmailOptIn(event.target.checked)} disabled={loading} />
            <span>Kampanya ve bilgilendirmeler için e-posta almak istiyorum. (İsteğe bağlı)</span>
          </label>
          <label style={{ display: "flex", gap: "0.6rem" }}>
            <input type="checkbox" checked={marketingSmsOptIn} onChange={(event) => setMarketingSmsOptIn(event.target.checked)} disabled={loading} />
            <span>Kampanya ve bilgilendirmeler için SMS almak istiyorum. (İsteğe bağlı)</span>
          </label>
        </fieldset>
        <button
          type="submit"
          disabled={loading || !legalDocuments.ready || !termsAccepted || !explicitConsentAccepted}
          style={{ width: "100%", padding: "0.75rem", backgroundColor: "#28a745", color: "white", border: "none", borderRadius: "4px", cursor: loading || !legalDocuments.ready ? "not-allowed" : "pointer", fontSize: "1rem" }}
        >
          {loading ? "Kayıt Yapılıyor..." : "Kayıt Ol"}
        </button>
      </form>
      <p style={{ marginTop: "1.5rem", textAlign: "center", marginBottom: 0 }}>
        Zaten hesabınız var mı? <Link to="/login">Giriş Yap</Link>
      </p>
    </div>
  );
};
