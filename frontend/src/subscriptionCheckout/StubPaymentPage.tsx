import { useEffect, useMemo, useState } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { subscriptionCheckoutApi } from "./subscriptionCheckoutApi";
import { paymentIdFromStubToken } from "./stubPaymentToken";
import "./stub-payment-page.css";

function formatAmount(amount: number) {
  return new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" }).format(amount);
}

export function StubPaymentPage() {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();
  const paymentId = useMemo(() => paymentIdFromStubToken(token), [token]);
  const [dashboard, setDashboard] = useState<StudentDashboardResponse | null>(null);
  const [state, setState] = useState<"loading" | "ready" | "submitting" | "success" | "error">("loading");
  const [error, setError] = useState("");
  const enabled = import.meta.env.DEV
    && import.meta.env.VITE_ENABLE_STUB_PAYMENT_SUCCESS === "true";

  useEffect(() => {
    if (!enabled || !paymentId) return;
    let active = true;
    studentDashboardApi.getDashboardData()
      .then((response) => {
        if (!active) return;
        const matchesPayment = response.payment?.id === paymentId;
        const validState = response.payment?.status === "PENDING"
          && response.subscription?.status === "PENDING_PAYMENT";
        const alreadySucceeded = response.payment?.status === "SUCCESS"
          && response.subscription?.status === "ACTIVE";
        if (!matchesPayment || (!validState && !alreadySucceeded)) {
          setError("Bu test ödeme oturumu mevcut hesabınızla eşleşmiyor veya artık kullanılamıyor.");
          setState("error");
          return;
        }
        setDashboard(response);
        setState(alreadySucceeded ? "success" : "ready");
      })
      .catch(() => {
        if (active) {
          setError("Ödeme özeti şu anda yüklenemiyor. Lütfen tekrar deneyin.");
          setState("error");
        }
      });
    return () => { active = false; };
  }, [enabled, paymentId]);

  if (!enabled) return <Navigate to="/dashboard" replace />;

  const handleSuccess = async () => {
    if (!paymentId || state === "submitting") return;
    setState("submitting");
    setError("");
    try {
      await subscriptionCheckoutApi.stubSucceed(paymentId);
      const refreshed = await studentDashboardApi.getDashboardData();
      if (refreshed.payment?.id !== paymentId || refreshed.payment.status !== "SUCCESS"
          || refreshed.subscription?.status !== "ACTIVE") {
        throw new Error("Payment state did not refresh");
      }
      setDashboard(refreshed);
      setState("success");
    } catch (cause) {
      setError(cause instanceof ApiError && cause.code === "COACH_FULL"
        ? "Koçun kontenjanı dolduğu için test ödemesi tamamlanamadı."
        : "Test ödemesi tamamlanamadı. Ödeme durumu değiştirilmedi; lütfen tekrar deneyin.");
      setState("error");
    }
  };

  const subscription = dashboard?.subscription;
  const payment = dashboard?.payment;
  const backPath = subscription?.coachId ? `/coaches/${subscription.coachId}` : "/dashboard";

  return (
    <div className="stub-payment">
      <section className="stub-payment__shell" aria-labelledby="stub-payment-title">
        <header className="stub-payment__header">
          <span className="stub-payment__mark" aria-hidden="true">U</span>
          <div><small>Uniform Akademi · Yerel Test</small><h1 id="stub-payment-title">Güvenli Ödeme Simülasyonu</h1></div>
        </header>

        {state === "loading" && <div className="stub-payment__state" role="status">Ödeme bilgileri hazırlanıyor…</div>}

        {dashboard && (
          <div className="stub-payment__summary">
            <p><span>Koç</span><strong>{subscription?.coachName}</strong></p>
            <p><span>Paket</span><strong>{subscription?.packageName}</strong></p>
            <p><span>Ödenecek tutar</span><strong>{payment ? formatAmount(payment.amount) : "—"}</strong></p>
            <p><span>İşlem numarası</span><strong>#{paymentId}</strong></p>
          </div>
        )}

        {state === "success" ? (
          <div className="stub-payment__success" role="status">
            <span aria-hidden="true">✓</span>
            <div><h2>Test ödemesi başarıyla tamamlandı</h2><p>Aboneliğiniz aktif edildi. Koçunuz ve görüşme haklarınız panelinizde hazır.</p></div>
          </div>
        ) : error ? (
          <div className="stub-payment__error" role="alert">{error}</div>
        ) : null}

        <div className="stub-payment__notice">
          Bu ekran yalnızca yerel geliştirme ortamında çalışır. Gerçek kart veya para işlemi yapılmaz.
        </div>

        <div className="stub-payment__actions">
          {state === "success" ? (
            <Link className="stub-payment__primary" to="/dashboard">Öğrenci Paneline Git</Link>
          ) : (
            <button className="stub-payment__primary" type="button" disabled={state !== "ready" && state !== "error" || !dashboard} onClick={() => void handleSuccess()}>
              {state === "submitting" ? "Ödeme Onaylanıyor…" : "Test Ödemesini Başarılı Yap"}
            </button>
          )}
          <button className="stub-payment__secondary" type="button" onClick={() => navigate(backPath)}>
            İptal Et ve Geri Dön
          </button>
        </div>
      </section>
    </div>
  );
}
