import { useEffect, useMemo, useState } from "react";
import { ApiError } from "../api/ApiError";
import { coachDashboardApi } from "../coachDashboard/coachDashboardApi";
import type { AvailabilityResponse } from "../booking/bookingTypes";
import type { TrialConsultationResponse } from "../trial/trialConsultationTypes";
import { rollingTrialDays } from "./trialAvailabilityDates";
import "./coach-trial-availability.css";

const ISTANBUL_OFFSET_HOURS = 3;
const statusLabels: Record<TrialConsultationResponse["status"], string> = {
  REQUESTED: "Onay bekliyor", CONFIRMED: "Onaylandı", COMPLETED: "Tamamlandı",
  CANCELLED: "İptal edildi", NO_SHOW: "Katılmadı",
};

function slotIso(day: Date, hour: number, minute: number) {
  return new Date(Date.UTC(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate(),
    hour - ISTANBUL_OFFSET_HOURS, minute)).toISOString();
}

const slotTimes = Array.from({ length: 16 }, (_, index) => ({
  hour: 9 + Math.floor(index / 2), minute: index % 2 === 0 ? 0 : 30,
}));

export const CoachTrialAvailabilityPage = () => {
  const [availability, setAvailability] = useState<AvailabilityResponse[]>([]);
  const [trials, setTrials] = useState<TrialConsultationResponse[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(() => Date.now());
  const days = useMemo(() => rollingTrialDays(), []);

  const load = async () => {
    try {
      const [slots, requests] = await Promise.all([
        coachDashboardApi.getTrialAvailability(), coachDashboardApi.getTrialConsultations(),
      ]);
      setAvailability(slots);
      setSelected(new Set(slots.map((slot) => new Date(slot.startTime).toISOString())));
      setTrials(requests);
    } catch { setError("Ücretsiz görüşme bilgileri alınamadı."); }
    finally { setLoading(false); }
  };

  useEffect(() => {
    const initialLoad = window.setTimeout(() => void load(), 0);
    const timer = window.setInterval(() => setCurrentTime(Date.now()), 60_000);
    return () => { window.clearTimeout(initialLoad); window.clearInterval(timer); };
  }, []);

  const booked = useMemo(() => new Set(availability.filter((slot) => slot.booked)
    .map((slot) => new Date(slot.startTime).toISOString())), [availability]);

  const toggle = (iso: string) => {
    if (booked.has(iso) || Date.parse(iso) <= currentTime) return;
    setNotice(null);
    setSelected((current) => {
      const next = new Set(current); if (next.has(iso)) next.delete(iso); else next.add(iso); return next;
    });
  };

  const save = async () => {
    if (saving) return;
    setSaving(true); setError(null); setNotice(null);
    try {
      const saved = await coachDashboardApi.saveTrialAvailability([...selected].sort());
      setAvailability(saved); setSelected(new Set(saved.map((slot) => new Date(slot.startTime).toISOString())));
      setNotice("Ücretsiz görüşme uygunluğunuz kaydedildi.");
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.detail : "Uygunluk kaydedilemedi. Lütfen tekrar deneyin.");
    } finally { setSaving(false); }
  };

  return <main className="trial-availability-page">
    <header><p>Koç takvimi</p><h1>Ücretsiz Görüşme Uygunluğu</h1><span>Bugün dahil önümüzdeki 7 gün için 30 dakikalık tanışma saatlerinizi belirleyin.</span></header>
    {error && <p className="trial-availability-page__error" role="alert">{error}</p>}
    {notice && <p className="trial-availability-page__success" role="status">{notice}</p>}
    <section className="trial-availability-page__panel" aria-label="Yedi günlük uygunluk seçimi">
      {loading ? <p>Uygunluk yükleniyor…</p> : <div className="trial-availability-page__days">
        {days.map((day) => <article key={day.toISOString()} className="trial-availability-page__day">
          <h2>{new Intl.DateTimeFormat("tr-TR", { timeZone: "UTC", weekday: "long" }).format(day)}</h2>
          <p>{new Intl.DateTimeFormat("tr-TR", { timeZone: "UTC", day: "numeric", month: "long" }).format(day)}</p>
          <div>{slotTimes.map(({ hour, minute }) => {
            const iso = slotIso(day, hour, minute); const isPast = Date.parse(iso) <= currentTime;
            const isBooked = booked.has(iso); const isSelected = selected.has(iso);
            const endHour = minute === 30 ? hour + 1 : hour; const endMinute = minute === 30 ? "00" : "30";
            const label = `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}–${String(endHour).padStart(2, "0")}:${endMinute}`;
            return <button key={iso} type="button" className={isSelected ? "is-selected" : ""}
              disabled={isPast || isBooked} aria-pressed={isSelected} onClick={() => toggle(iso)}
              title={isBooked ? "Bu saat rezerve edildi" : isPast ? "Geçmiş saat seçilemez" : undefined}>
              {label}{isBooked && <small>Rezerve</small>}
            </button>;
          })}</div>
        </article>)}
      </div>}
      <button className="trial-availability-page__save" type="button" disabled={loading || saving} onClick={() => void save()}>{saving ? "Kaydediliyor…" : "Seçimi Kaydet"}</button>
    </section>
    <section className="trial-availability-page__panel" aria-labelledby="trial-requests-title">
      <h2 id="trial-requests-title">Ücretsiz Görüşme Talepleri</h2>
      {loading ? <p>Talepler yükleniyor…</p> : trials.length === 0 ? <p>Henüz bir ücretsiz görüşme talebi yok.</p> : <div className="trial-availability-page__requests">
        {trials.map((trial) => <article key={trial.id}><div><strong>{trial.studentName}</strong><span>{new Date(trial.startsAt).toLocaleString("tr-TR", { dateStyle: "long", timeStyle: "short" })}</span></div><em>{statusLabels[trial.status]}</em>{trial.meetingUrl && <a href={trial.meetingUrl} target="_blank" rel="noreferrer">Görüşmeye katıl</a>}</article>)}
      </div>}
    </section>
  </main>;
};
