import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { authApi } from "../auth/authApi";
import { homePathForUser } from "../auth/authNavigation";
import { useAuth } from "../auth/AuthProvider";
import { FormError } from "../components/FormError";
import { LegalDocumentViewer } from "../legal/LegalDocumentViewer";
import { legalErrorMessage, isStaleLegalDocumentError } from "../legal/legalErrors";
import { useLegalDocuments } from "../legal/useLegalDocuments";
import { AuthPageShell } from "./AuthPageShell";
import "./auth-page.css";
import "./legal-onboarding.css";

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
    <AuthPageShell
      title="Hukuki onayları tamamlayın"
      lead="Platform özelliklerini kullanmaya devam etmek için güncel metinleri inceleyip zorunlu onayları tamamlayın."
      icon="✓"
      wide
    >
      <div className="legal-onboarding">
        <div className="legal-onboarding__error"><FormError error={error} /></div>
        {legalDocuments.loading && <div className="legal-onboarding__state" role="status">Hukuki metinler yükleniyor…</div>}
        {legalDocuments.error && (
          <div className="legal-onboarding__state legal-onboarding__state--error" role="alert">
            <span>Hukuki metinler yüklenemedi.</span>
            <button type="button" onClick={() => void legalDocuments.reload()}>Yeniden Dene</button>
          </div>
        )}

        <form className="legal-onboarding__form" onSubmit={handleSubmit}>
          <p className="legal-onboarding__kvkk">
            <LegalDocumentViewer label="KVKK Aydınlatma Metni" document={legalDocuments.documents.KVKK_NOTICE} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} />
            ’ni inceleyebilirsiniz.
          </p>

          <section className="legal-onboarding__group" aria-labelledby="required-approvals-title">
            <h2 id="required-approvals-title">Zorunlu onaylar</h2>
            <div className="legal-onboarding__choices">
              <div className="legal-onboarding__choice legal-onboarding__choice--required">
                <input id="onboarding-terms" type="checkbox" checked={termsAccepted} onChange={(event) => setTermsAccepted(event.target.checked)} disabled={!legalDocuments.ready || submitting} />
                <div className="legal-onboarding__choice-content">
                  <label htmlFor="onboarding-terms">Kullanım Koşulları’nı okudum ve kabul ediyorum. <strong>(Zorunlu)</strong></label>
                  <LegalDocumentViewer label="Metni görüntüle" document={legalDocuments.documents.TERMS_OF_USE} />
                </div>
              </div>
              <div className="legal-onboarding__choice legal-onboarding__choice--required">
                <input id="onboarding-explicit-consent" type="checkbox" checked={explicitConsentAccepted} onChange={(event) => setExplicitConsentAccepted(event.target.checked)} disabled={!legalDocuments.ready || submitting} />
                <div className="legal-onboarding__choice-content">
                  <label htmlFor="onboarding-explicit-consent">Açık Rıza Metni’ni okudum ve kabul ediyorum. <strong>(Zorunlu)</strong></label>
                  <LegalDocumentViewer label="Metni görüntüle" document={legalDocuments.documents.EXPLICIT_CONSENT} />
                </div>
              </div>
            </div>
          </section>

          <section className="legal-onboarding__group legal-onboarding__group--optional" aria-labelledby="optional-consents-title">
            <h2 id="optional-consents-title">İletişim tercihleri</h2>
            <div className="legal-onboarding__choices">
              <label className="legal-onboarding__choice">
                <input type="checkbox" checked={marketingEmailOptIn} onChange={(event) => setMarketingEmailOptIn(event.target.checked)} disabled={submitting} />
                <span>E-posta ile kampanya iletileri almak istiyorum. <strong>(İsteğe bağlı)</strong></span>
              </label>
              <label className="legal-onboarding__choice">
                <input type="checkbox" checked={marketingSmsOptIn} onChange={(event) => setMarketingSmsOptIn(event.target.checked)} disabled={submitting} />
                <span>SMS ile kampanya iletileri almak istiyorum. <strong>(İsteğe bağlı)</strong></span>
              </label>
            </div>
          </section>

          <div className="legal-onboarding__actions">
            <button className="legal-onboarding__primary" type="submit" disabled={submitting || !legalDocuments.ready || !termsAccepted || !explicitConsentAccepted}>
              {submitting ? "Onaylar kaydediliyor…" : "Onayla ve devam et"}
            </button>
            <button className="legal-onboarding__secondary" type="button" disabled={submitting} onClick={() => void logout().then(() => navigate("/login", { replace: true }))}>Çıkış yap</button>
          </div>
        </form>
      </div>
    </AuthPageShell>
  );
};
