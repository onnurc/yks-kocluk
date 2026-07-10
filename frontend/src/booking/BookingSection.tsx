import React, { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { bookingApi } from "./bookingApi";
import type { AvailabilityResponse } from "./bookingTypes";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { canBookWithSubscription } from "../access/subscriptionAccess";
import { FormError } from "../components/FormError";

interface BookingSectionProps {
  coachId: number;
  dashboardData: StudentDashboardResponse | null;
}

export const BookingSection: React.FC<BookingSectionProps> = ({
  coachId,
  dashboardData,
}) => {
  const [slots, setSlots] = useState<AvailabilityResponse[]>([]);
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [bookingLoading, setBookingLoading] = useState<boolean>(false);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [error, setError] = useState<any | null>(null);

  const sub = dashboardData?.subscription;
  const isSubscribedToThisCoach = sub?.coachId === coachId;
  const isEligible = isSubscribedToThisCoach && canBookWithSubscription(sub?.status);
  const isPendingPayment = sub?.status === "PENDING_PAYMENT";

  const fetchAvailability = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await bookingApi.listCoachAvailability(coachId);
      // Filter out already booked slots just in case
      setSlots(data.filter((slot) => !slot.booked));
    } catch (err) {
      setError(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAvailability();
  }, [coachId]);

  const handleBook = async () => {
    if (!selectedSlotId || bookingLoading || !isEligible) return;

    setBookingLoading(true);
    setError(null);
    setSuccessMessage(null);

    try {
      await bookingApi.book({ availabilityId: selectedSlotId });
      setSuccessMessage("Randevunuz başarıyla oluşturuldu! Görüşme detaylarınızı Randevularım sayfasından inceleyebilirsiniz.");
      setSelectedSlotId(null);
      await fetchAvailability();
    } catch (err: any) {
      setError(err);
    } finally {
      setBookingLoading(false);
    }
  };

  const formatSlotTime = (isoString: string) => {
    const d = new Date(isoString);
    return d.toLocaleString("tr-TR", {
      day: "numeric",
      month: "long",
      weekday: "long",
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  // Gracefully map API error codes
  let customErrorMessage = "";
  if (error) {
    const code = error.code || error.errorCode;
    if (code === "NO_ACTIVE_SUBSCRIPTION") {
      customErrorMessage = "Bu koç ile aktif bir aboneliğiniz bulunmuyor.";
    } else if (code === "QUOTA_EXCEEDED") {
      customErrorMessage = "Bu hafta için seans kotanız doldu. Lütfen sonraki haftalardan randevu almayı deneyin.";
    } else if (code === "SLOT_TAKEN") {
      customErrorMessage = "Seçtiğiniz randevu saati az önce rezerve edildi. Lütfen başka bir slot seçin.";
    } else if (code === "SLOT_IN_PAST") {
      customErrorMessage = "Geçmiş bir tarihe randevu alınamaz.";
    }
  }

  return (
    <div style={{ marginTop: "2rem", borderTop: "2px solid #dee2e6", paddingTop: "1.5rem" }}>
      <h2 style={{ marginBottom: "1rem", color: "#333" }}>📅 Randevu Al</h2>

      {/* Warnings & Banners */}
      {!isEligible && (
        <div style={{ padding: "1rem", border: "1px solid #ffeeba", borderRadius: "8px", backgroundColor: "#fff3cd", color: "#856404", marginBottom: "1.5rem" }}>
          {isPendingPayment ? (
            <p style={{ margin: 0 }}>
              ⚠️ Ödemeniz henüz tamamlanmadığı için randevu oluşturulamaz. Lütfen{" "}
              <Link to="/dashboard" style={{ fontWeight: "bold", color: "#856404", textDecoration: "underline" }}>
                kontrol panelinden ödeme yapın.
              </Link>
            </p>
          ) : !isSubscribedToThisCoach ? (
            <p style={{ margin: 0 }}>
              ⚠️ Sadece aktif abonesi olduğunuz koç ile randevu oluşturabilirsiniz. Paket satın almak için aşağıdaki
              abonelik paketlerini kullanabilirsiniz.
            </p>
          ) : (
            <p style={{ margin: 0 }}>
              ⚠️ Randevu oluşturmak için aktif bir aboneliğiniz olmalıdır. Abonelik durumunuz: <strong>{sub?.status}</strong>.
            </p>
          )}
        </div>
      )}

      {successMessage && (
        <div style={{ padding: "1rem", border: "1px solid #c3e6cb", borderRadius: "8px", backgroundColor: "#d4edda", color: "#155724", marginBottom: "1.5rem" }}>
          🎉 {successMessage}
          <div style={{ marginTop: "0.5rem" }}>
            <Link to="/bookings" style={{ color: "#155724", fontWeight: "bold", textDecoration: "underline" }}>
              Randevularım Sayfasına Git &rarr;
            </Link>
          </div>
        </div>
      )}

      {customErrorMessage ? (
        <div style={{ padding: "0.75rem", border: "1px solid #f5c6cb", borderRadius: "4px", backgroundColor: "#f8d7da", color: "#721c24", marginBottom: "1.5rem" }}>
          ⚠️ {customErrorMessage}
        </div>
      ) : (
        <FormError error={error} />
      )}

      {loading ? (
        <p>Randevu saatleri yükleniyor...</p>
      ) : slots.length === 0 ? (
        <p style={{ fontStyle: "italic", color: "#6c757d" }}>
          Koçun henüz tanımlanmış boş randevu saati bulunmuyor.
        </p>
      ) : (
        <div>
          <p style={{ marginBottom: "1rem", fontSize: "0.95rem", color: "#495057" }}>
            Lütfen aşağıdaki boş randevu saatlerinden birini seçin:
          </p>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))", gap: "0.75rem", marginBottom: "1.5rem" }}>
            {slots.map((slot) => {
              const isSelected = selectedSlotId === slot.id;
              return (
                <div
                  key={slot.id}
                  onClick={() => isEligible && setSelectedSlotId(slot.id)}
                  style={{
                    padding: "1rem",
                    border: isSelected ? "2px solid #28a745" : "1px solid #ced4da",
                    borderRadius: "6px",
                    backgroundColor: isSelected ? "#e2f0d9" : "#fff",
                    cursor: isEligible ? "pointer" : "not-allowed",
                    opacity: isEligible ? 1 : 0.6,
                    transition: "all 0.15s ease",
                    textAlign: "center",
                    boxShadow: isSelected ? "0 2px 6px rgba(40,167,69,0.15)" : "none",
                  }}
                >
                  <span style={{ fontWeight: "bold", display: "block", color: isSelected ? "#1e3d13" : "#333" }}>
                    {formatSlotTime(slot.startTime)}
                  </span>
                </div>
              );
            })}
          </div>

          <button
            onClick={handleBook}
            disabled={!selectedSlotId || bookingLoading || !isEligible}
            style={{
              padding: "0.75rem 1.5rem",
              backgroundColor: !selectedSlotId || !isEligible ? "#6c757d" : "#28a745",
              color: "white",
              border: "none",
              borderRadius: "4px",
              fontWeight: "bold",
              cursor: !selectedSlotId || bookingLoading || !isEligible ? "not-allowed" : "pointer",
              fontSize: "1rem",
              width: "100%",
            }}
          >
            {bookingLoading ? "Rezervasyon Yapılıyor..." : "Randevu Oluştur"}
          </button>
        </div>
      )}
    </div>
  );
};
