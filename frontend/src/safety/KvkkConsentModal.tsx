import React, { useState } from "react";
import { useAuth } from "../auth/AuthProvider";
import { safetyApi } from "./safetyApi";
import { ApiError } from "../api/ApiError";

export const KvkkConsentModal: React.FC = () => {
  const { logout, setHasConsented, consentVersion, consentStatus } = useAuth();
  const [checked, setChecked] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const handleConfirm = async () => {
    if (!checked || isSubmitting) return;
    setIsSubmitting(true);
    setErrorMsg(null);

    try {
      await safetyApi.recordConsent("KVKK", consentVersion);
      setHasConsented(true);
    } catch (err) {
      if (err instanceof ApiError) {
        setErrorMsg(err.detail || "Onay kaydedilirken bir hata oluştu.");
      } else {
        setErrorMsg("Sunucuya ulaşılamadı. Lütfen tekrar deneyin.");
      }
      setIsSubmitting(false);
    }
  };

  const handleLogout = async () => {
    try {
      await logout();
    } catch {
      setErrorMsg("Çıkış yapılırken bir hata oluştu.");
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="kvkk-title"
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        backgroundColor: "rgba(15, 23, 42, 0.85)",
        backdropFilter: "blur(8px)",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        zIndex: 9999,
        padding: "1rem",
      }}
    >
      <div
        style={{
          backgroundColor: "#1e293b",
          color: "#f8fafc",
          borderRadius: "16px",
          width: "100%",
          maxWidth: "600px",
          padding: "2rem",
          boxShadow: "0 25px 50px -12px rgba(0, 0, 0, 0.5)",
          border: "1px solid #334155",
          display: "flex",
          flexDirection: "column",
          maxHeight: "90vh",
        }}
      >
        <h2 id="kvkk-title" style={{ margin: "0 0 1rem 0", color: "#38bdf8", fontSize: "1.5rem", fontWeight: "700" }}>
          KVKK Aydınlatma Metni ve Açık Rıza Beyanı
        </h2>
        <div style={{ fontSize: "0.85rem", color: "#94a3b8", marginBottom: "1rem" }}>
          Versiyon: <strong>{consentVersion}</strong>
        </div>

        {consentStatus === "REVOKED" ? (
          <div style={{ backgroundColor: "#7f1d1d", color: "#fca5a5", padding: "0.75rem 1rem", borderRadius: "8px", marginBottom: "1rem", fontSize: "0.9rem", border: "1px solid #ef4444" }}>
            <strong>Dikkat:</strong> Veli onayınız geri çekilmiştir/iptal edilmiştir. Platformu kullanmaya devam edebilmek için velinizin yeniden onay vermesi gerekmektedir.
          </div>
        ) : (
          <div style={{ backgroundColor: "#1e293b", color: "#94a3b8", padding: "0.75rem 1rem", borderRadius: "8px", marginBottom: "1rem", fontSize: "0.9rem", border: "1px solid #334155" }}>
            <strong>Bilgi:</strong> 18 yaş altı kullanıcılarımızın platformu kullanabilmesi için veli onayı gerekmektedir. Lütfen aşağıdaki aydınlatma metnini velinizle birlikte inceleyip onaylayınız.
          </div>
        )}

        {errorMsg && (
          <div
            style={{
              padding: "1rem",
              backgroundColor: "rgba(239, 68, 68, 0.15)",
              border: "1px solid #ef4444",
              borderRadius: "8px",
              color: "#fca5a5",
              fontSize: "0.9rem",
              marginBottom: "1rem",
            }}
          >
            {errorMsg}
          </div>
        )}

        <div
          style={{
            flex: 1,
            overflowY: "auto",
            padding: "1rem",
            backgroundColor: "#0f172a",
            borderRadius: "8px",
            border: "1px solid #1e293b",
            lineHeight: "1.6",
            fontSize: "0.95rem",
            color: "#cbd5e1",
            marginBottom: "1.5rem",
          }}
        >
          <p style={{ marginTop: 0 }}>
            <strong>YKS Koçluk Platformu</strong> olarak, 6698 sayılı Kişisel Verilerin Korunması Kanunu ("KVKK") uyarınca, kişisel verilerinizin güvenliği ve işlenmesi süreçlerinde sizleri bilgilendirmek isteriz.
          </p>
          <p>
            Platformumuzu kullanırken sağladığınız ad, soyad, e-posta adresi, profil detayları ve koçluk görüşmeleri kapsamında paylaştığınız akademik/bireysel verileriniz; sizlere daha iyi koçluk hizmeti sunabilmek, rezervasyon ve mesajlaşma fonksiyonlarını yürütebilmek ve güvenlik standartlarını sağlamak amacıyla işlenmektedir.
          </p>
          <p>
            Kişisel verileriniz, kanuni yükümlülüklerin yerine getirilmesi veya platform içi güvenliğin denetlenmesi (örneğin kötüye kullanım raporlarının incelenmesi) durumları haricinde izniniz olmaksızın üçüncü taraflarla paylaşılmayacaktır.
          </p>
          <p>
            Bu bilgilendirme metnini okuyup onaylayarak, kişisel verilerinizin yukarıda belirtilen kapsamlarda işlenmesine açık rıza vermektesiniz.
          </p>
        </div>

        <div style={{ display: "flex", alignItems: "flex-start", gap: "0.75rem", marginBottom: "1.5rem" }}>
          <input
            id="kvkk-checkbox"
            type="checkbox"
            checked={checked}
            onChange={(e) => setChecked(e.target.checked)}
            disabled={isSubmitting}
            style={{
              marginTop: "0.25rem",
              width: "1.2rem",
              height: "1.2rem",
              cursor: isSubmitting ? "not-allowed" : "pointer",
            }}
          />
          <label
            htmlFor="kvkk-checkbox"
            style={{
              fontSize: "0.9rem",
              color: "#cbd5e1",
              cursor: isSubmitting ? "not-allowed" : "pointer",
              userSelect: "none",
            }}
          >
            Yukarıdaki KVKK Aydınlatma Metnini okudum, anladım ve verilerimin bu doğrultuda işlenmesini kabul ediyorum.
          </label>
        </div>

        <div style={{ display: "flex", justifyContent: "space-between", gap: "1rem" }}>
          <button
            onClick={handleLogout}
            disabled={isSubmitting}
            style={{
              padding: "0.75rem 1.5rem",
              backgroundColor: "transparent",
              color: "#94a3b8",
              border: "1px solid #475569",
              borderRadius: "8px",
              cursor: isSubmitting ? "not-allowed" : "pointer",
              fontSize: "0.95rem",
              fontWeight: "600",
              transition: "all 0.2s",
            }}
          >
            Çıkış Yap
          </button>
          <button
            onClick={handleConfirm}
            disabled={!checked || isSubmitting}
            style={{
              padding: "0.75rem 2rem",
              backgroundColor: checked && !isSubmitting ? "#0ea5e9" : "#334155",
              color: checked && !isSubmitting ? "#ffffff" : "#94a3b8",
              border: "none",
              borderRadius: "8px",
              cursor: checked && !isSubmitting ? "pointer" : "not-allowed",
              fontSize: "0.95rem",
              fontWeight: "600",
              transition: "all 0.2s",
            }}
          >
            {isSubmitting ? "Onaylanıyor..." : "Onayla ve Devam Et"}
          </button>
        </div>
      </div>
    </div>
  );
};
