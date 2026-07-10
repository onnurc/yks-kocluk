import React, { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { subscriptionCheckoutApi } from "./subscriptionCheckoutApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { FormError } from "../components/FormError";

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
  const [error, setError] = useState<any | null>(null);

  const subStatus = dashboardData?.subscription?.status;
  const isPending = subStatus === "PENDING_PAYMENT";
  const isActive = subStatus === "ACTIVE";
  const isEligible = user?.role === "STUDENT" && !isPending && !isActive;

  const handleCheckout = async () => {
    if (!isEligible || loading) return;

    setLoading(true);
    setError(null);

    try {
      const response = await subscriptionCheckoutApi.checkout({
        coachId,
        packageId,
      });

      if (response && response.checkoutUrl) {
        // Accept only valid http/https URLs and perform same-tab navigation
        if (response.checkoutUrl.startsWith("http://") || response.checkoutUrl.startsWith("https://")) {
          window.location.assign(response.checkoutUrl);
        } else {
          throw new Error("Geçersiz ödeme yönlendirme adresi.");
        }
      } else {
        throw new Error("Ödeme oturumu adresi alınamadı.");
      }
    } catch (err: any) {
      setError(err);
      setLoading(false);
    }
  };

  // Check button label and state
  let buttonLabel = "Ödemeye Geç";
  let buttonDisabled = !isEligible || loading;

  if (loading) {
    buttonLabel = "Ödeme Oturumu Hazırlanıyor...";
  } else if (isPending) {
    buttonLabel = "Bekleyen Ödeme Mevcut";
  } else if (isActive) {
    buttonLabel = "Aktif Abonelik Mevcut";
  } else if (user?.role !== "STUDENT") {
    buttonLabel = "Yalnızca Öğrenciler Satın Alabilir";
    buttonDisabled = true;
  }

  // Handle specialized API error codes or status
  let customErrorMessage = "";
  if (error) {
    const status = error.status;
    const code = error.code || error.errorCode;

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
          {(error.status === 409 || error.code === "ALREADY_SUBSCRIBED") && (
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
