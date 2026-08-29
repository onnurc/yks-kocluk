import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { bookingApi } from "../booking/bookingApi";
import type { SessionResponse, SessionStatus } from "../booking/bookingTypes";
import { trialConsultationApi } from "../trial/trialConsultationApi";
import type { TrialConsultationResponse } from "../trial/trialConsultationTypes";
import "./bookings-page.css";

const statusLabels: Record<SessionStatus, string> = {
  PLANNED: "Planlandı",
  COMPLETED: "Tamamlandı",
  CANCELLED: "İptal edildi",
  LATE_CANCELLED: "Geç iptal",
  NO_SHOW: "Katılmadı",
};

const formatDay = (value: string) => new Intl.DateTimeFormat("tr-TR", { day: "2-digit" }).format(new Date(value));
const formatMonth = (value: string) => new Intl.DateTimeFormat("tr-TR", { month: "short" }).format(new Date(value)).replace(".", "");
const formatDate = (value: string) => new Intl.DateTimeFormat("tr-TR", {
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
}).format(new Date(value));
const formatTimeRange = (start: string, end: string) => {
  const formatter = new Intl.DateTimeFormat("tr-TR", { hour: "2-digit", minute: "2-digit" });
  return `${formatter.format(new Date(start))} – ${formatter.format(new Date(end))}`;
};

function CalendarIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="4" y="5" width="16" height="15" rx="2" /><path d="M8 3v5M16 3v5M4 10h16" /></svg>;
}

function ClockIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></svg>;
}

function VideoIcon() {
  return <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3.5" y="6" width="11.5" height="12" rx="2" /><path d="m15 10 5-2v8l-5-2" /></svg>;
}

export const BookingsPage = () => {
  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [trials, setTrials] = useState<TrialConsultationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  const retry = async () => {
    setLoading(true);
    setError(false);
    try {
      const data = await bookingApi.listMySessions();
      setSessions(data || []);
    } catch {
      setError(true);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    let active = true;
    bookingApi.listMySessions().then((data) => {
      if (active) setSessions(data || []);
    }).catch(() => {
      if (active) setError(true);
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    trialConsultationApi.myTrials().then((data) => { if (active) setTrials(data || []); }).catch(() => undefined);
    return () => { active = false; };
  }, []);

  return (
    <section className="bookings-page" aria-labelledby="bookings-title">
      <header className="bookings-page__header">
        <div>
          <p className="bookings-page__eyebrow">Mentorluk takvimi</p>
          <h1 id="bookings-title">Görüşmelerim</h1>
          <p className="bookings-page__lead">Planlanan ve geçmiş koçluk görüşmelerinizi tek yerde takip edin.</p>
        </div>
        {!loading && !error && sessions.length > 0 && <Link className="bookings-page__primary-action" to="/coaches">Koçları Keşfet <span aria-hidden="true">→</span></Link>}
      </header>

      {loading ? (
        <div className="bookings-page__loading" role="status" aria-label="Görüşmeler yükleniyor">
          <span /><span /><span />
        </div>
      ) : error ? (
        <div className="bookings-page__state bookings-page__state--error" role="alert">
          <span className="bookings-page__state-icon"><CalendarIcon /></span>
          <h2>Görüşmeler yüklenemedi</h2>
          <p>Bilgilerinizi şu anda alamıyoruz. Lütfen yeniden deneyin.</p>
          <button type="button" onClick={() => void retry()}>Yeniden dene</button>
        </div>
      ) : sessions.length === 0 && trials.length === 0 ? (
        <div className="bookings-page__state" aria-label="Boş görüşmeler durumu">
          <span className="bookings-page__state-icon"><CalendarIcon /></span>
          <p className="bookings-page__eyebrow">Görüşmeler</p>
          <h2>Kayıtlı görüşmeniz bulunmuyor.</h2>
          <p>Size uygun koçu keşfederek gerçek profilindeki uygun saatlerden görüşme planlayabilirsiniz.</p>
          <Link className="bookings-page__primary-action" to="/coaches">Koçları Keşfet <span aria-hidden="true">→</span></Link>
        </div>
      ) : (
        <div className="bookings-page__list" aria-label="Görüşme listesi">
          {trials.map((trial) => (
            <article className="bookings-page__card" key={`trial-${trial.id}`}>
              <time className="bookings-page__date-tile" dateTime={trial.startsAt}><span>{formatMonth(trial.startsAt)}</span><strong>{formatDay(trial.startsAt)}</strong></time>
              <div className="bookings-page__details"><div className="bookings-page__title-row"><h2>{trial.coachName} · Ücretsiz Tanışma</h2><span className={`bookings-page__status bookings-page__status--${trial.status.toLowerCase()}`}>{trial.status === "REQUESTED" ? "Onay bekliyor" : trial.status === "CONFIRMED" ? "Onaylandı" : trial.status === "COMPLETED" ? "Tamamlandı" : trial.status === "CANCELLED" ? "İptal edildi" : "Katılmadı"}</span></div><p><CalendarIcon /> <span>{formatDate(trial.startsAt)}</span></p><p><ClockIcon /> <span>{formatTimeRange(trial.startsAt, trial.endsAt)}</span></p></div>
              {trial.status === "CONFIRMED" && trial.meetingUrl ? <a className="bookings-page__join" href={trial.meetingUrl} target="_blank" rel="noreferrer"><VideoIcon/><span>Görüşmeye katıl</span></a> : trial.status === "REQUESTED" ? <span className="bookings-page__join bookings-page__join--disabled"><VideoIcon/><span>Bağlantı bekleniyor</span></span> : null}
            </article>
          ))}
          {sessions.map((session) => {
            const canJoin = session.status === "PLANNED" && Boolean(session.meetLink);
            return (
              <article className="bookings-page__card" key={session.id}>
                <time className="bookings-page__date-tile" dateTime={session.startTime}>
                  <span>{formatMonth(session.startTime)}</span>
                  <strong>{formatDay(session.startTime)}</strong>
                </time>
                <div className="bookings-page__details">
                  <div className="bookings-page__title-row">
                    <h2>{session.coachName}</h2>
                    <span className={`bookings-page__status bookings-page__status--${session.status.toLowerCase()}`}>{statusLabels[session.status]}</span>
                  </div>
                  <p><CalendarIcon /> <span>{formatDate(session.startTime)}</span></p>
                  <p><ClockIcon /> <span>{formatTimeRange(session.startTime, session.endTime)}</span></p>
                </div>
                {canJoin ? (
                  <a className="bookings-page__join" href={session.meetLink!} target="_blank" rel="noreferrer" aria-label={`${session.coachName} ile görüşmeye katıl`}>
                    <VideoIcon /><span>Görüşmeye katıl</span>
                  </a>
                ) : session.status === "PLANNED" ? (
                  <span className="bookings-page__join bookings-page__join--disabled" aria-label="Görüşme bağlantısı henüz hazır değil">
                    <VideoIcon /><span>Bağlantı bekleniyor</span>
                  </span>
                ) : null}
              </article>
            );
          })}
        </div>
      )}
    </section>
  );
};
