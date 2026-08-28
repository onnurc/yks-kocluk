import React, { useCallback, useEffect, useState } from "react";
import { trialConsultationApi } from "./trialConsultationApi";
import type { TrialConsultationResponse } from "./trialConsultationTypes";
import type { AvailabilityResponse } from "../booking/bookingTypes";
import { ApiError } from "../api/ApiError";
import "./trial-consultation.css";

interface TrialConsultationSectionProps {
  coachId: number;
}

function formatSlot(iso: string): { date: string; time: string } {
  const d = new Date(iso);
  return {
    date: d.toLocaleDateString("tr-TR", { day: "numeric", month: "long", weekday: "long" }),
    time: d.toLocaleTimeString("tr-TR", { hour: "2-digit", minute: "2-digit" }),
  };
}

const ACTIVE_STATUSES = new Set(["REQUESTED", "CONFIRMED"]);

const CalendarIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3.5" y="5" width="17" height="16" rx="3" />
    <path d="M3.5 10h17M8 3v4M16 3v4" />
  </svg>
);

const ClockIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="9" />
    <path d="M12 7v5l3.5 2" />
  </svg>
);

export const TrialConsultationSection: React.FC<TrialConsultationSectionProps> = ({ coachId }) => {
  const [existingTrial, setExistingTrial] = useState<TrialConsultationResponse | null>(null);
  const [slots, setSlots] = useState<AvailabilityResponse[]>([]);
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [error, setError] = useState<ApiError | Error | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [trials, availability] = await Promise.all([
        trialConsultationApi.myTrials(),
        trialConsultationApi.listCoachTrialAvailability(coachId),
      ]);
      const active = trials.find((t) => t.coachProfileId === coachId && ACTIVE_STATUSES.has(t.status)) ?? null;
      setExistingTrial(active);
      setSlots(availability.filter((s) => !s.booked));
    } catch (err) {
      setError(err instanceof Error ? err : new Error("Deneme görüşmesi bilgisi alınamadı."));
    } finally {
      setLoading(false);
    }
  }, [coachId]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleRequest = async () => {
    if (!selectedSlotId || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      const trial = await trialConsultationApi.request({ availabilityId: selectedSlotId });
      setExistingTrial(trial);
      setSelectedSlotId(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error("Görüşme talebi oluşturulamadı."));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async () => {
    if (!existingTrial || cancelling) return;
    setCancelling(true);
    setError(null);
    try {
      await trialConsultationApi.cancel(existingTrial.id);
      setExistingTrial(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err : new Error("Görüşme iptal edilemedi."));
    } finally {
      setCancelling(false);
    }
  };

  let errorMessage = "";
  if (error) {
    const code = error instanceof ApiError ? error.code : undefined;
    errorMessage =
      code === "SLOT_TAKEN"
        ? "Seçtiğiniz saat az önce dolmuş. Lütfen başka bir saat seçin."
        : code === "SLOT_IN_PAST"
          ? "Geçmiş bir saate görüşme planlanamaz."
          : code === "COACH_NOT_AVAILABLE"
            ? "Bu koç şu anda deneme görüşmesine açık değil."
            : code === "TRIAL_ALREADY_EXISTS"
              ? "Bu koçla zaten bekleyen veya onaylanmış bir deneme görüşmeniz var."
              : error instanceof ApiError
                ? error.detail || error.title
                : error.message;
  }

  return (
    <div className="trial-card">
      <div className="trial-card__glow" />
      <div className="trial-card__body">
        <h3>Ücretsiz Tanışma Görüşmesi</h3>
        <p>
          Koçunla 20-30 dakikalık ücretsiz bir ön görüşme yaparak hedeflerini paylaşabilir, sana uygun çalışma
          planını birlikte konuşabilirsiniz.
        </p>

        {errorMessage && <div className="trial-card__error">{errorMessage}</div>}

        {loading ? (
          <p className="trial-card__hint">Uygunluk bilgisi yükleniyor…</p>
        ) : existingTrial ? (
          <div className="trial-card__scheduled">
            <div className="trial-card__row">
              <CalendarIcon />
              <span>{formatSlot(existingTrial.startsAt).date}</span>
            </div>
            <div className="trial-card__row">
              <ClockIcon />
              <span>{formatSlot(existingTrial.startsAt).time}</span>
            </div>
            <p className="trial-card__status">
              Durum: {existingTrial.status === "REQUESTED" ? "Onay bekliyor" : "Onaylandı"}
            </p>
            <button className="trial-card__cancel" onClick={handleCancel} disabled={cancelling}>
              {cancelling ? "İptal ediliyor…" : "Görüşmeyi İptal Et"}
            </button>
          </div>
        ) : slots.length === 0 ? (
          <p className="trial-card__hint">Bu koçun şu anda tanımlı uygun deneme görüşmesi saati bulunmuyor.</p>
        ) : (
          <>
            <div className="trial-card__slots">
              {slots.slice(0, 6).map((slot) => {
                const { date, time } = formatSlot(slot.startTime);
                const selected = selectedSlotId === slot.id;
                return (
                  <button
                    key={slot.id}
                    type="button"
                    className={`trial-card__slot${selected ? " trial-card__slot--selected" : ""}`}
                    onClick={() => setSelectedSlotId(slot.id)}
                  >
                    <span className="trial-card__slot-date">{date}</span>
                    <span className="trial-card__slot-time">{time}</span>
                  </button>
                );
              })}
            </div>
            <button className="trial-card__submit" onClick={handleRequest} disabled={!selectedSlotId || submitting}>
              {submitting ? "Gönderiliyor…" : "Ücretsiz Görüşme Planla"}
            </button>
          </>
        )}
      </div>
    </div>
  );
};
