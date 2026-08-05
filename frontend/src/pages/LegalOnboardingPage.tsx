import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { authApi } from "../auth/authApi";
import { homePathForUser } from "../auth/authNavigation";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";
import { LegalDocumentViewer } from "../legal/LegalDocumentViewer";
import { legalErrorMessage, isStaleLegalDocumentError } from "../legal/legalErrors";
import { useLegalDocuments } from "../legal/useLegalDocuments";

const ONBOARDING_DOCUMENT_TYPES = ["TERMS_OF_USE", "EXPLICIT_CONSENT", "KVKK_NOTICE"] as const;

export const LegalOnboardingPage: React.FC = () => {
  const { user, refreshCurrentUser, logout } = useAuth();
  const navigate = useNavigate();
  const legalDocuments = useLegalDocuments([...ONBOARDING_DOCUMENT_TYPES]);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [explicitConsentAccepted, setExplicitConsentAccepted] = useState(false);
  const [marketingEmailOptIn, setMarketingEmailOptIn] = useState(false);
  const [marketingSmsOptIn, setMarketingSmsOptIn] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<Error | string | null>(null);

  useEffect(() => {
    if (user?.legalOnboardingCompleted) navigate(homePathForUser(user), { replace: true });
  }, [navigate, user]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!legalDocuments.ready || !termsAccepted || !explicitConsentAccepted) {
      setError("Devam etmek için zorunlu hukuki metinleri onaylayın.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await authApi.completeLegalOnboarding({
        termsDocumentId: legalDocuments.documents.TERMS_OF_USE!.id,
        explicitConsentDocumentId: legalDocuments.documents.EXPLICIT_CONSENT!.id,
        marketingEmailOptIn,
        marketingSmsOptIn,
      });
      await refreshCurrentUser();
      const refreshed = await authApi.getCurrentUser();
      navigate(homePathForUser(refreshed), { replace: true });
    } catch (cause) {
      setError(legalErrorMessage(cause) || (cause instanceof Error ? cause : "Hukuki onay tamamlanamadı."));
      if (isStaleLegalDocumentError(cause)) {
        setTermsAccepted(false);
        setExplicitConsentAccepted(false);
        await legalDocuments.reload();
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main style={{ maxWidth: "620px", margin: "3rem auto", padding: "2rem", border: "1px solid #cbd5e1", borderRadius: "12px", background: "white" }}>
      <h1 style={{ marginTop: 0 }}>Hukuki onayları tamamlayın</h1>
      <p>Platform özelliklerini kullanmaya devam etmek için güncel metinleri inceleyip zorunlu onayları tamamlayın.</p>
      <FormError error={error} />
      {legalDocuments.loading && <p role="status">Hukuki metinler yükleniyor…</p>}
      {legalDocuments.error && <p role="alert">Hukuki metinler yüklenemedi. <button type="button" onClick={() => void legalDocuments.reload()}>Yeniden Dene</button></p>}

      <form onSubmit={handleSubmit}>
        <p>
          <LegalDocumentViewer label="KVKK Aydınlatma Metni" document={legalDocuments.documents.KVKK_NOTICE} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} />
          ’ni inceleyebilirsiniz.
        </p>
        <div style={{ display: "flex", gap: ".75rem", margin: "1rem 0" }}>
          <input id="onboarding-terms" type="checkbox" checked={termsAccepted} onChange={(event) => setTermsAccepted(event.target.checked)} disabled={!legalDocuments.ready || submitting} />
          <div><label htmlFor="onboarding-terms">Kullanım Koşulları’nı okudum ve kabul ediyorum. <strong>(Zorunlu)</strong></label>{" "}<LegalDocumentViewer label="Metni görüntüle" document={legalDocuments.documents.TERMS_OF_USE} /></div>
        </div>
        <div style={{ display: "flex", gap: ".75rem", margin: "1rem 0" }}>
          <input id="onboarding-explicit-consent" type="checkbox" checked={explicitConsentAccepted} onChange={(event) => setExplicitConsentAccepted(event.target.checked)} disabled={!legalDocuments.ready || submitting} />
          <div><label htmlFor="onboarding-explicit-consent">Açık Rıza Metni’ni okudum ve kabul ediyorum. <strong>(Zorunlu)</strong></label>{" "}<LegalDocumentViewer label="Metni görüntüle" document={legalDocuments.documents.EXPLICIT_CONSENT} /></div>
        </div>
        <label style={{ display: "flex", gap: ".75rem", margin: "1rem 0" }}><input type="checkbox" checked={marketingEmailOptIn} onChange={(event) => setMarketingEmailOptIn(event.target.checked)} disabled={submitting} /> E-posta ile kampanya iletileri almak istiyorum. (İsteğe bağlı)</label>
        <label style={{ display: "flex", gap: ".75rem", margin: "1rem 0" }}><input type="checkbox" checked={marketingSmsOptIn} onChange={(event) => setMarketingSmsOptIn(event.target.checked)} disabled={submitting} /> SMS ile kampanya iletileri almak istiyorum. (İsteğe bağlı)</label>
        <button type="submit" disabled={submitting || !legalDocuments.ready || !termsAccepted || !explicitConsentAccepted} style={{ width: "100%", padding: ".8rem", border: 0, borderRadius: "6px", background: "#0284c7", color: "white", fontWeight: 700, cursor: "pointer" }}>
          {submitting ? "Onaylar kaydediliyor…" : "Onayla ve devam et"}
        </button>
      </form>
      <button type="button" onClick={() => void logout().then(() => navigate("/login", { replace: true }))} style={{ width: "100%", marginTop: ".75rem", padding: ".7rem", border: "1px solid #94a3b8", borderRadius: "6px", background: "white" }}>Çıkış yap</button>
    </main>
  );
};
