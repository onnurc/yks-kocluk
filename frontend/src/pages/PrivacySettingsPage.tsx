import React, { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { useAuth } from "../auth/AuthProvider";
import { ConfirmationModal } from "../components/ConfirmationModal";
import { FormError } from "../components/FormError";
import { LegalDocumentViewer } from "../legal/LegalDocumentViewer";
import { useLegalDocuments } from "../legal/useLegalDocuments";
import { privacyApi } from "../privacy/privacyApi";
import type { AccountDeletionResponse, MarketingPreferences, PrivacyPreferences } from "../privacy/privacyTypes";
import "./privacy-settings.css";

export const PrivacySettingsPage: React.FC = () => {
  const { refreshCurrentUser, clearSession } = useAuth();
  const navigate = useNavigate();
  const cookieDocument = useLegalDocuments(["COOKIE_POLICY"]);
  const [marketing, setMarketing] = useState<MarketingPreferences | null>(null);
  const [privacy, setPrivacy] = useState<PrivacyPreferences | null>(null);
  const [deletionStatus, setDeletionStatus] = useState<AccountDeletionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState<Error | string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [withdrawOpen, setWithdrawOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteConfirmation, setDeleteConfirmation] = useState("");

  const loadPreferences = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [marketingResponse, privacyResponse, deletionResponse] = await Promise.all([
        privacyApi.getMarketingPreferences(),
        privacyApi.getPrivacyPreferences(),
        privacyApi.getAccountDeletion().catch((cause) => {
          if (cause instanceof ApiError && cause.status === 404) return null;
          throw cause;
        }),
      ]);
      setMarketing(marketingResponse);
      setPrivacy(privacyResponse);
      setDeletionStatus(deletionResponse);
    } catch (cause) {
      setError(cause instanceof Error ? cause : "Tercihler yüklenemedi.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void loadPreferences();
  }, [loadPreferences]);

  const saveMarketing = async (channel: "email" | "sms", granted: boolean) => {
    setSaving(channel);
    setError(null);
    setSuccess(null);
    try {
      setMarketing(await privacyApi.updateMarketingPreferences({ [channel]: granted }));
      setSuccess("Pazarlama tercihiniz kaydedildi.");
    } catch (cause) {
      setError(cause instanceof Error ? cause : "Tercih kaydedilemedi.");
    } finally {
      setSaving(null);
    }
  };

  const saveCookies = async () => {
    if (!privacy || !cookieDocument.ready) return;
    setSaving("cookies");
    setError(null);
    setSuccess(null);
    try {
      setPrivacy(await privacyApi.updatePrivacyPreferences({
        necessaryAllowed: true,
        analyticsAllowed: privacy.analyticsAllowed,
        marketingAllowed: privacy.marketingAllowed,
        cookiePolicyDocumentId: cookieDocument.documents.COOKIE_POLICY!.id,
      }));
      setSuccess("Çerez tercihleriniz kaydedildi.");
    } catch (cause) {
      setError(cause instanceof Error ? cause : "Çerez tercihleri kaydedilemedi.");
    } finally {
      setSaving(null);
    }
  };

  const withdrawConsent = async () => {
    setSaving("withdraw");
    setError(null);
    try {
      await privacyApi.withdrawExplicitConsent();
      await refreshCurrentUser();
      setWithdrawOpen(false);
      navigate("/legal-onboarding", { replace: true });
    } catch (cause) {
      setError(cause instanceof Error ? cause : "Açık rıza geri çekilemedi.");
      setWithdrawOpen(false);
    } finally {
      setSaving(null);
    }
  };

  const deleteAccount = async () => {
    if (deleteConfirmation !== "DELETE") return;
    setSaving("delete");
    setError(null);
    try {
      await privacyApi.deleteAccount();
      clearSession();
      navigate("/login?accountDeleted=1", { replace: true });
    } catch (cause) {
      setError(cause instanceof ApiError ? cause : "Hesap silme işlemi tamamlanamadı.");
      setDeleteOpen(false);
    } finally {
      setSaving(null);
    }
  };

  return (
    <div className="privacy-page">
      <header className="privacy-page__header">
        <h1>Gizlilik ve hukuki tercihler</h1>
        <p>İsteğe bağlı iletişim ve çerez tercihlerinizi yönetin. İşlemsel hizmet iletileri bu pazarlama tercihlerinden etkilenmez.</p>
      </header>

      <div className="privacy-page__notices">
        <div className="privacy-page__error"><FormError error={error} /></div>
        {success && <div className="privacy-page__success" role="status">{success}</div>}
      </div>

      {loading && <div className="privacy-page__loading" role="status">Tercihler yükleniyor…</div>}

      <div className="privacy-page__preference-grid">
        {marketing && (
          <section className="privacy-page__card" aria-labelledby="marketing-preferences-title" aria-busy={saving === "email" || saving === "sms"}>
            <div className="privacy-page__section-heading">
              <h2 id="marketing-preferences-title">Pazarlama tercihleri</h2>
            </div>
            <div className="privacy-page__options">
              <label className="privacy-page__option">
                <input type="checkbox" checked={marketing.email.granted} disabled={saving !== null} onChange={(event) => void saveMarketing("email", event.target.checked)} />
                <span>E-posta ile kampanya iletileri</span>
              </label>
              <label className="privacy-page__option">
                <input type="checkbox" checked={marketing.sms.granted} disabled={saving !== null} onChange={(event) => void saveMarketing("sms", event.target.checked)} />
                <span>SMS ile kampanya iletileri</span>
              </label>
            </div>
            {saving === "email" || saving === "sms" ? <small className="privacy-page__saving">Kaydediliyor…</small> : null}
          </section>
        )}

        {privacy && (
          <section className="privacy-page__card" aria-labelledby="cookie-preferences-title" aria-busy={saving === "cookies"}>
            <div className="privacy-page__section-heading privacy-page__section-heading--with-link">
              <h2 id="cookie-preferences-title">Çerez tercihleri</h2>
              <p className="privacy-page__policy-link"><LegalDocumentViewer label="Çerez Politikası" document={cookieDocument.documents.COOKIE_POLICY} loading={cookieDocument.loading} error={cookieDocument.error} onRetry={() => void cookieDocument.reload()} /></p>
            </div>
            <div className="privacy-page__options">
              <label className="privacy-page__option privacy-page__option--locked"><input type="checkbox" checked disabled /> <span>Zorunlu — her zaman açık</span></label>
              <label className="privacy-page__option"><input type="checkbox" checked={privacy.analyticsAllowed} disabled={saving !== null} onChange={(event) => setPrivacy({ ...privacy, analyticsAllowed: event.target.checked })} /> <span>Analitik — isteğe bağlı</span></label>
              <label className="privacy-page__option"><input type="checkbox" checked={privacy.marketingAllowed} disabled={saving !== null} onChange={(event) => setPrivacy({ ...privacy, marketingAllowed: event.target.checked })} /> <span>Pazarlama — isteğe bağlı</span></label>
            </div>
            <div className="privacy-page__card-actions">
              <button className="privacy-page__button privacy-page__button--primary" type="button" onClick={() => void saveCookies()} disabled={saving !== null || !cookieDocument.ready}>{saving === "cookies" ? "Kaydediliyor…" : "Çerez tercihlerini kaydet"}</button>
            </div>
            <p className="privacy-page__helper">Giriş yapmadan önceki çerez tercihleriniz bu tarayıcıda yerel olarak yönetilir.</p>
          </section>
        )}
      </div>

      <div className="privacy-page__action-grid">
        <section className="privacy-page__action-card privacy-page__action-card--warning" aria-labelledby="withdraw-consent-title">
          <div>
            <h2 id="withdraw-consent-title">Açık rızayı geri çekme</h2>
            <p>Geri çektiğinizde hesabınız silinmez; ödeme ve hukuki kayıtlar saklanır. Yeniden onay verene kadar ödeme, rezervasyon ve mesajlaşma gibi korunan işlemleri kullanamazsınız.</p>
          </div>
          <button className="privacy-page__button privacy-page__button--warning" type="button" disabled={saving !== null} onClick={() => setWithdrawOpen(true)}>Açık rızayı geri çek</button>
        </section>

        <section className="privacy-page__action-card privacy-page__action-card--danger" aria-labelledby="delete-account-title">
          <div>
            <h2 id="delete-account-title">Hesabı sil</h2>
            <p>Bu işlem hesabınızı anonimleştirir ve oturumunuzu kapatır. Yasal olarak tutulması gereken finansal ve sözleşmesel kayıtlar saklanabilir.</p>
            {deletionStatus && <p className="privacy-page__deletion-status"><strong>Mevcut talep durumu:</strong> {deletionStatus.status}</p>}
          </div>
          <button className="privacy-page__button privacy-page__button--danger" type="button" disabled={saving !== null} onClick={() => setDeleteOpen(true)}>Hesabımı sil</button>
        </section>
      </div>

      <ConfirmationModal open={withdrawOpen} title="Açık rızayı geri çek" confirmLabel="Rızayı geri çek" busy={saving === "withdraw"} onClose={() => setWithdrawOpen(false)} onConfirm={() => void withdrawConsent()}>
        <p>Korunan işlemler yeniden hukuki onay verene kadar kullanılamaz. Hesabınız otomatik olarak silinmez ve mevcut finansal/hukuki kayıtlar korunur.</p>
      </ConfirmationModal>

      <ConfirmationModal open={deleteOpen} title="Hesabı kalıcı olarak anonimleştir" confirmLabel="Hesabı sil" destructive busy={saving === "delete"} confirmDisabled={deleteConfirmation !== "DELETE"} onClose={() => { setDeleteOpen(false); setDeleteConfirmation(""); }} onConfirm={() => void deleteAccount()}>
        <p>Devam etmek için aşağıdaki alana <strong>DELETE</strong> yazın.</p>
        <input className="privacy-page__confirmation-input" aria-label="Hesap silme onayı" value={deleteConfirmation} onChange={(event) => setDeleteConfirmation(event.target.value)} autoComplete="off" />
      </ConfirmationModal>
    </div>
  );
};
