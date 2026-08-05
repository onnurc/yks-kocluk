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
    <div style={{ maxWidth: "760px", margin: "0 auto", padding: "2rem" }}>
      <h1>Gizlilik ve hukuki tercihler</h1>
      <p>İsteğe bağlı iletişim ve çerez tercihlerinizi yönetin. İşlemsel hizmet iletileri bu pazarlama tercihlerinden etkilenmez.</p>
      <FormError error={error} />
      {success && <div role="status" style={{ padding: ".75rem", marginBottom: "1rem", background: "#dcfce7", color: "#166534", borderRadius: "6px" }}>{success}</div>}
      {loading && <p role="status">Tercihler yükleniyor…</p>}

      {marketing && (
        <section style={{ padding: "1.25rem", marginBottom: "1rem", border: "1px solid #cbd5e1", borderRadius: "8px", background: "white" }}>
          <h2>Pazarlama tercihleri</h2>
          <label style={{ display: "flex", gap: ".75rem", margin: "1rem 0" }}><input type="checkbox" checked={marketing.email.granted} disabled={saving !== null} onChange={(event) => void saveMarketing("email", event.target.checked)} /> E-posta ile kampanya iletileri</label>
          <label style={{ display: "flex", gap: ".75rem", margin: "1rem 0" }}><input type="checkbox" checked={marketing.sms.granted} disabled={saving !== null} onChange={(event) => void saveMarketing("sms", event.target.checked)} /> SMS ile kampanya iletileri</label>
          {saving === "email" || saving === "sms" ? <small>Kaydediliyor…</small> : null}
        </section>
      )}

      {privacy && (
        <section style={{ padding: "1.25rem", marginBottom: "1rem", border: "1px solid #cbd5e1", borderRadius: "8px", background: "white" }}>
          <h2>Çerez tercihleri</h2>
          <p><LegalDocumentViewer label="Çerez Politikası" document={cookieDocument.documents.COOKIE_POLICY} loading={cookieDocument.loading} error={cookieDocument.error} onRetry={() => void cookieDocument.reload()} /></p>
          <label style={{ display: "flex", gap: ".75rem", margin: "1rem 0" }}><input type="checkbox" checked disabled /> Zorunlu — her zaman açık</label>
          <label style={{ display: "flex", gap: ".75rem", margin: "1rem 0" }}><input type="checkbox" checked={privacy.analyticsAllowed} disabled={saving !== null} onChange={(event) => setPrivacy({ ...privacy, analyticsAllowed: event.target.checked })} /> Analitik — isteğe bağlı</label>
          <label style={{ display: "flex", gap: ".75rem", margin: "1rem 0" }}><input type="checkbox" checked={privacy.marketingAllowed} disabled={saving !== null} onChange={(event) => setPrivacy({ ...privacy, marketingAllowed: event.target.checked })} /> Pazarlama — isteğe bağlı</label>
          <button type="button" onClick={() => void saveCookies()} disabled={saving !== null || !cookieDocument.ready}>{saving === "cookies" ? "Kaydediliyor…" : "Çerez tercihlerini kaydet"}</button>
          <p style={{ color: "#64748b", fontSize: ".85rem" }}>Giriş yapmadan önceki çerez tercihleriniz bu tarayıcıda yerel olarak yönetilir.</p>
        </section>
      )}

      <section style={{ padding: "1.25rem", marginBottom: "1rem", border: "1px solid #f59e0b", borderRadius: "8px", background: "#fffbeb" }}>
        <h2>Açık rızayı geri çekme</h2>
        <p>Geri çektiğinizde hesabınız silinmez; ödeme ve hukuki kayıtlar saklanır. Yeniden onay verene kadar ödeme, rezervasyon ve mesajlaşma gibi korunan işlemleri kullanamazsınız.</p>
        <button type="button" onClick={() => setWithdrawOpen(true)}>Açık rızayı geri çek</button>
      </section>

      <section style={{ padding: "1.25rem", border: "1px solid #dc2626", borderRadius: "8px", background: "#fef2f2" }}>
        <h2>Hesabı sil</h2>
        <p>Bu işlem hesabınızı anonimleştirir ve oturumunuzu kapatır. Yasal olarak tutulması gereken finansal ve sözleşmesel kayıtlar saklanabilir.</p>
        {deletionStatus && <p><strong>Mevcut talep durumu:</strong> {deletionStatus.status}</p>}
        <button type="button" onClick={() => setDeleteOpen(true)} style={{ color: "#b91c1c" }}>Hesabımı sil</button>
      </section>

      <ConfirmationModal open={withdrawOpen} title="Açık rızayı geri çek" confirmLabel="Rızayı geri çek" busy={saving === "withdraw"} onClose={() => setWithdrawOpen(false)} onConfirm={() => void withdrawConsent()}>
        <p>Korunan işlemler yeniden hukuki onay verene kadar kullanılamaz. Hesabınız otomatik olarak silinmez ve mevcut finansal/hukuki kayıtlar korunur.</p>
      </ConfirmationModal>

      <ConfirmationModal open={deleteOpen} title="Hesabı kalıcı olarak anonimleştir" confirmLabel="Hesabı sil" destructive busy={saving === "delete"} confirmDisabled={deleteConfirmation !== "DELETE"} onClose={() => { setDeleteOpen(false); setDeleteConfirmation(""); }} onConfirm={() => void deleteAccount()}>
        <p>Devam etmek için aşağıdaki alana <strong>DELETE</strong> yazın.</p>
        <input aria-label="Hesap silme onayı" value={deleteConfirmation} onChange={(event) => setDeleteConfirmation(event.target.value)} autoComplete="off" style={{ width: "100%", boxSizing: "border-box", padding: ".65rem" }} />
      </ConfirmationModal>
    </div>
  );
};
