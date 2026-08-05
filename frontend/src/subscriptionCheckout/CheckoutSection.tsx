import React, { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { subscriptionCheckoutApi } from "./subscriptionCheckoutApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { FormError } from "../components/FormError";
import { LegalDocumentViewer } from "../legal/LegalDocumentViewer";
import { isStaleLegalDocumentError, legalErrorMessage } from "../legal/legalErrors";
import { useLegalDocuments } from "../legal/useLegalDocuments";
import { ApiError } from "../api/ApiError";

const CHECKOUT_DOCUMENT_TYPES = ["PRE_INFORMATION_FORM", "DISTANCE_SALES_AGREEMENT", "REFUND_CANCELLATION_POLICY"] as const;

interface CheckoutSectionProps {
  coachId: number;
  coachName: string;
  packageId: number;
  packageName: string;
  price: number;
  dashboardData: StudentDashboardResponse | null;
}

export const CheckoutSection: React.FC<CheckoutSectionProps> = ({
  coachId,
  coachName,
  packageId,
  packageName,
  price,
  dashboardData,
}) => {
  const { user } = useAuth();
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<ApiError | Error | string | null>(null);
  const [checkoutUrl, setCheckoutUrl] = useState<string | null>(null);
  const [legalDocumentsAccepted, setLegalDocumentsAccepted] = useState(false);
  const legalDocuments = useLegalDocuments([...CHECKOUT_DOCUMENT_TYPES]);

  const subStatus = dashboardData?.subscription?.status;
  const isPending = subStatus === "PENDING_PAYMENT";
  const isActive = subStatus === "ACTIVE";
  const isEligible = user?.role === "STUDENT" && user.legalOnboardingCompleted && !isPending && !isActive;

  const handleCheckout = async () => {
    if (!isEligible || loading || !legalDocuments.ready || !legalDocumentsAccepted) return;

    setLoading(true);
    setError(null);
    setCheckoutUrl(null);

    try {
      const response = await subscriptionCheckoutApi.checkout({
        coachId,
        packageId,
        preInformationDocumentId: legalDocuments.documents.PRE_INFORMATION_FORM!.id,
        distanceSalesDocumentId: legalDocuments.documents.DISTANCE_SALES_AGREEMENT!.id,
        refundCancellationPolicyDocumentId: legalDocuments.documents.REFUND_CANCELLATION_POLICY!.id,
        legalDocumentsAccepted: true,
      });

      if (response && response.checkoutUrl) {
        try {
          const parsed = new URL(response.checkoutUrl);
          const allowedHosts = [
            "sandbox-api.iyzipay.com",
            "sandbox-api.iyzico.com",
            "api.iyzico.com",
            "api.iyzipay.com",
            "www.iyzico.com",
          ];
          if (import.meta.env.DEV) {
            allowedHosts.push("checkout.stub.local");
          }
          if (parsed.protocol === "https:" && allowedHosts.includes(parsed.hostname)) {
            setCheckoutUrl(response.checkoutUrl);
          } else {
            throw new Error("Güvenli olmayan veya izin verilmeyen ödeme yönlendirme adresi.");
          }
        } catch (cause: unknown) {
          const message = cause instanceof Error ? cause.message : "Geçersiz ödeme yönlendirme adresi.";
          throw new Error(message, { cause });
        }
      } else {
        throw new Error("Ödeme oturumu adresi alınamadı.");
      }
    } catch (err: unknown) {
      setError(legalErrorMessage(err) || (err instanceof Error ? err : "Ödeme işlemi başlatılamadı."));
      if (isStaleLegalDocumentError(err)) {
        setLegalDocumentsAccepted(false);
        await legalDocuments.reload();
      }
    } finally {
      setLoading(false);
    }
  };

  // Check button label and state
  let buttonLabel = "Ödemeye Geç";
  let buttonDisabled = !isEligible || loading || !legalDocuments.ready || !legalDocumentsAccepted;

  if (loading) {
    buttonLabel = "Ödeme Oturumu Hazırlanıyor...";
  } else if (isPending) {
    buttonLabel = "Bekleyen Ödeme Mevcut";
  } else if (isActive) {
    buttonLabel = "Aktif Abonelik Mevcut";
  } else if (user?.role !== "STUDENT") {
    buttonLabel = "Yalnızca Öğrenciler Satın Alabilir";
    buttonDisabled = true;
  } else if (!user.legalOnboardingCompleted) {
    buttonLabel = "Önce Hukuki Onayları Tamamlayın";
  } else if (legalDocuments.loading) {
    buttonLabel = "Hukuki Metinler Yükleniyor...";
  } else if (!legalDocumentsAccepted) {
    buttonLabel = "Sözleşmeleri Kabul Edin";
  }

  // Handle specialized API error codes or status
  let customErrorMessage = "";
  if (error) {
    const status = error instanceof ApiError ? error.status : undefined;
    const code = error instanceof ApiError ? error.code : undefined;

    if (status === 409 || code === "ALREADY_SUBSCRIBED") {
      customErrorMessage = "Bu koç ile zaten aktif veya bekleyen bir aboneliğiniz var.";
    } else if (code === "NOT_PAYMENT_OWNER") {
      customErrorMessage = "Bu işlem size ait değil.";
    } else if (code === "COACH_FULL") {
      customErrorMessage = "Seçilen koçun kontenjanı doludur.";
    } else if (status === 404) {
      customErrorMessage = "Seçilen koç veya paket bulunamadı. Lütfen sayfayı yenileyip tekrar deneyin.";
    }
  }

  return (
    <div style={{ padding: "1.5rem", border: "1px solid #b8daff", borderRadius: "8px", backgroundColor: "#e2f0d9", color: "#2e5c1e", marginTop: "1.5rem" }}>
      <h3 style={{ marginTop: 0, color: "#1e3d13" }}>Seçilen Paket Özeti</h3>
      <div style={{ marginBottom: "1rem", fontSize: "0.95rem" }}>
        <p style={{ margin: "0.25rem 0" }}><strong>Koç:</strong> {coachName}</p>
        <p style={{ margin: "0.25rem 0" }}><strong>Paket:</strong> {packageName}</p>
        <p style={{ margin: "0.25rem 0" }}><strong>Tutar:</strong> {price} TRY</p>
      </div>

      {customErrorMessage ? (
        <div style={{ padding: "0.75rem", border: "1px solid #f5c6cb", borderRadius: "4px", backgroundColor: "#f8d7da", color: "#721c24", marginBottom: "1rem", fontSize: "0.9rem" }}>
          ⚠️ {customErrorMessage}
          {(error instanceof ApiError && (error.status === 409 || error.code === "ALREADY_SUBSCRIBED")) && (
            <div style={{ marginTop: "0.5rem" }}>
              <Link to="/dashboard" style={{ color: "#721c24", fontWeight: "bold", textDecoration: "underline" }}>
                Panele Geri Dön
              </Link>
            </div>
          )}
        </div>
      ) : (
        <FormError error={error} />
      )}

      {isPending && (
        <div style={{ padding: "0.75rem", border: "1px solid #ffeeba", borderRadius: "4px", backgroundColor: "#fff3cd", color: "#856404", marginBottom: "1rem", fontSize: "0.9rem" }}>
          ⚠️ Zaten bekleyen bir ödeme işleminiz var. Yeni paket seçimi checkout aşamasında engellenecek.
        </div>
      )}

      {isActive && (
        <div style={{ padding: "0.75rem", border: "1px solid #bee5eb", borderRadius: "4px", backgroundColor: "#d1ecf1", color: "#0c5460", marginBottom: "1rem", fontSize: "0.9rem" }}>
          ℹ️ Aktif aboneliğiniz bulunduğu için yeni checkout bu aşamada başlatılamaz.
        </div>
      )}

      {!isPending && !isActive && !error && (
        <div style={{ padding: "0.75rem", border: "1px solid #ced4da", borderRadius: "4px", backgroundColor: "#f8f9fa", color: "#495057", marginBottom: "1rem", fontSize: "0.9rem" }}>
          ℹ️ "Ödemeye Geç" butonuna tıkladığınızda ödeme sayfasına yönlendirileceksiniz.
        </div>
      )}

      {!isPending && !isActive && (
        <div style={{ padding: "1rem", border: "1px solid #cbd5e1", borderRadius: "6px", background: "#fff", color: "#334155", marginBottom: "1rem" }}>
          <p style={{ marginTop: 0, fontWeight: 700 }}>Ödeme öncesi hukuki metinler</p>
          {legalDocuments.loading && <p role="status">Hukuki metinler yükleniyor…</p>}
          {legalDocuments.error && (
            <p role="alert" style={{ color: "#b91c1c" }}>
              Hukuki metinler yüklenemedi. <button type="button" onClick={() => void legalDocuments.reload()}>Yeniden Dene</button>
            </p>
          )}
          <ul style={{ paddingLeft: "1.25rem" }}>
            <li><LegalDocumentViewer label="Ön Bilgilendirme Formu" document={legalDocuments.documents.PRE_INFORMATION_FORM} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} /></li>
            <li><LegalDocumentViewer label="Mesafeli Satış Sözleşmesi" document={legalDocuments.documents.DISTANCE_SALES_AGREEMENT} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} /></li>
            <li><LegalDocumentViewer label="İade / İptal Politikası" document={legalDocuments.documents.REFUND_CANCELLATION_POLICY} loading={legalDocuments.loading} error={legalDocuments.error} onRetry={() => void legalDocuments.reload()} /></li>
          </ul>
          <div style={{ display: "flex", alignItems: "flex-start", gap: ".65rem" }}>
            <input id="accept-checkout-documents" type="checkbox" checked={legalDocumentsAccepted} onChange={(event) => setLegalDocumentsAccepted(event.target.checked)} disabled={!legalDocuments.ready || loading || !user?.legalOnboardingCompleted} />
            <label htmlFor="accept-checkout-documents">Ön Bilgilendirme Formu’nu, Mesafeli Satış Sözleşmesi’ni ve İade / İptal Politikası’nı okudum ve kabul ediyorum. <strong>(Zorunlu)</strong></label>
          </div>
        </div>
      )}

      {checkoutUrl && (
        <div style={{ marginTop: "1rem", marginBottom: "1rem" }}>
          <a
            href={checkoutUrl}
            target="_blank"
            rel="noopener noreferrer"
            style={{
              display: "block",
              textAlign: "center",
              padding: "0.75rem",
              backgroundColor: "#0284c7",
              color: "white",
              textDecoration: "none",
              borderRadius: "4px",
              fontWeight: "bold",
              fontSize: "1rem",
              transition: "background-color 0.2s",
            }}
          >
            İyzico Ödeme Sayfasını Aç (Yeni Sekmede)
          </a>
        </div>
      )}

      <button
        disabled={buttonDisabled}
        onClick={handleCheckout}
        style={{
          width: "100%",
          padding: "0.75rem",
          backgroundColor: buttonDisabled ? "#6c757d" : "#28a745",
          color: "white",
          border: "none",
          borderRadius: "4px",
          fontWeight: "bold",
          cursor: buttonDisabled ? "not-allowed" : "pointer",
          fontSize: "1rem",
          transition: "background-color 0.2s ease",
        }}
      >
        {buttonLabel}
      </button>
    </div>
  );
};
