/* eslint-disable react-hooks/set-state-in-effect -- dashboard sections load from independent existing APIs */
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { coachDashboardApi } from "../coachDashboard/coachDashboardApi";
import type { AvailabilityResponse, CoachDashboardSummaryResponse, SessionResponse } from "../coachDashboard/coachDashboardTypes";
import { MESSAGE_NOTIFICATION_EVENT } from "../messaging/useNotificationSocket";
import type { UserNotificationResponse } from "../messaging/messagingTypes";
import "./coach-dashboard.css";

const firstName = (name: string) => name.trim().split(/\s+/)[0] || name;
const dateFormatter = new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "long", weekday: "long" });
const timeFormatter = new Intl.DateTimeFormat("tr-TR", { hour: "2-digit", minute: "2-digit" });
const formatRange = (start: string, end: string) => `${timeFormatter.format(new Date(start))} – ${timeFormatter.format(new Date(end))}`;

function DashboardIcon({ name }: { name: "students" | "completed" | "planned" | "messages" | "clock" | "video" }) {
  const paths = {
    students: <><circle cx="9" cy="8" r="3" /><circle cx="17" cy="9" r="2.5" /><path d="M3.5 19c.4-4 2.3-6 5.5-6s5.1 2 5.5 6M14.5 14c3.3-.7 5.3 1 6 4" /></>,
    completed: <><circle cx="12" cy="12" r="9" /><path d="m8 12 2.6 2.6L16.5 9" /></>,
    planned: <><rect x="4" y="5" width="16" height="15" rx="2" /><path d="M8 3v5M16 3v5M4 10h16" /></>,
    messages: <><path d="M4 5h16v12H9l-5 4V5Z" /><path d="M8 9h8M8 13h5" /></>,
    clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
    video: <><rect x="3" y="6" width="13" height="12" rx="2" /><path d="m16 10 5-2v8l-5-2" /></>,
  };
  return <svg viewBox="0 0 24 24" aria-hidden="true">{paths[name]}</svg>;
}

export function CoachDashboardPage() {
  const { user } = useAuth();
  const [summary, setSummary] = useState<CoachDashboardSummaryResponse | null>(null);
  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [availability, setAvailability] = useState<AvailabilityResponse[]>([]);
  const [summaryError, setSummaryError] = useState(false);
  const [sessionsError, setSessionsError] = useState(false);
  const [availabilityError, setAvailabilityError] = useState(false);
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);
  const [dashboardLoadedAt] = useState(() => Date.now());

  useEffect(() => {
    let active = true;
    setLoading(true); setSummaryError(false); setSessionsError(false); setAvailabilityError(false);
    Promise.allSettled([coachDashboardApi.getSummary(), coachDashboardApi.getUpcomingSessions(), coachDashboardApi.getAvailability()])
      .then(([summaryResult, sessionsResult, availabilityResult]) => {
        if (!active) return;
        if (summaryResult.status === "fulfilled") setSummary(summaryResult.value); else setSummaryError(true);
        if (sessionsResult.status === "fulfilled") setSessions(sessionsResult.value.content ?? []); else setSessionsError(true);
        if (availabilityResult.status === "fulfilled") setAvailability(availabilityResult.value ?? []); else setAvailabilityError(true);
      }).finally(() => active && setLoading(false));
    return () => { active = false; };
  }, [version]);

  useEffect(() => {
    const updateUnread = (event: Event) => {
      const notification = (event as CustomEvent<UserNotificationResponse>).detail;
      if (notification) setSummary((current) => current ? { ...current, unreadMessageCount: notification.unreadTotal } : current);
    };
    const refresh = () => setVersion((value) => value + 1);
    window.addEventListener(MESSAGE_NOTIFICATION_EVENT, updateUnread);
    window.addEventListener("messages-read", refresh);
    return () => { window.removeEventListener(MESSAGE_NOTIFICATION_EVENT, updateUnread); window.removeEventListener("messages-read", refresh); };
  }, []);

  const openAvailability = useMemo(() => availability.filter((slot) => !slot.booked && new Date(slot.startTime).getTime() > dashboardLoadedAt).sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime)).slice(0, 4), [availability, dashboardLoadedAt]);
  const metrics = [
    { label: "Aktif Öğrenci", value: summary?.activeStudentCount, icon: "students" as const },
    { label: "Bu Ay Tamamlanan", value: summary?.completedSessionsThisMonth, icon: "completed" as const },
    { label: "Bekleyen Seanslar", value: summary?.upcomingSessionCount, icon: "planned" as const },
    { label: "Okunmamış Mesaj", value: summary?.unreadMessageCount, icon: "messages" as const },
  ];

  return (
    <div className="coach-dashboard" aria-busy={loading}>
      <header className="coach-dashboard__welcome"><p>Koç paneli</p><h1>Merhaba, {firstName(user?.fullName ?? "")}!</h1><span>Öğrencilerinizi, görüşmelerinizi ve müsaitliğinizi tek yerden takip edin.</span></header>
      <section className="coach-dashboard__metrics" aria-label="Koç paneli özeti">
        {metrics.map((metric) => <article key={metric.label} className="coach-dashboard__metric"><span><DashboardIcon name={metric.icon} /></span>{summaryError ? <em>—</em> : <strong>{metric.value ?? (loading ? "…" : 0)}</strong>}<h2>{metric.label}</h2></article>)}
      </section>
      {summaryError && <div className="coach-dashboard__error" role="alert">Panel özeti alınamadı. <button type="button" onClick={() => setVersion((value) => value + 1)}>Yeniden dene</button></div>}

      <div className="coach-dashboard__grid">
        <section className="coach-dashboard__panel" aria-labelledby="coach-sessions-title">
          <div className="coach-dashboard__section-heading"><div><p>Takvim</p><h2 id="coach-sessions-title">Yaklaşan Görüşmeler</h2></div>{summary?.pendingTrialConsultationCount ? <span>{summary.pendingTrialConsultationCount} deneme talebi</span> : null}</div>
          {sessionsError ? <div className="coach-dashboard__empty">Görüşmeler şu anda alınamadı.</div> : sessions.length === 0 ? <div className="coach-dashboard__empty"><strong>Yaklaşan görüşmeniz yok.</strong><span>Planlanan seanslar burada görünecek.</span></div> : <div className="coach-dashboard__session-list">{sessions.map((session) => <article className="coach-dashboard__session" key={session.id}><time dateTime={session.startTime}><strong>{new Intl.DateTimeFormat("tr-TR", { day: "2-digit" }).format(new Date(session.startTime))}</strong><span>{new Intl.DateTimeFormat("tr-TR", { month: "short" }).format(new Date(session.startTime)).replace(".", "")}</span></time><div><h3>{session.studentName}</h3><p>{dateFormatter.format(new Date(session.startTime))}</p><span><DashboardIcon name="clock" />{formatRange(session.startTime, session.endTime)}</span></div>{session.meetLink ? <a href={session.meetLink} target="_blank" rel="noreferrer" aria-label={`${session.studentName} ile görüşmeye katıl`}><DashboardIcon name="video" /></a> : <span className="coach-dashboard__no-join" aria-label="Google Meet bağlantısını mesajlar üzerinden öğrenciye gönderin"><DashboardIcon name="video" /></span>}</article>)}</div>}
        </section>

        <div className="coach-dashboard__side">
          <Link className="coach-dashboard__messages" to="/messages"><span className="coach-dashboard__messages-icon"><DashboardIcon name="messages" /></span><div><p>Mesajlar</p><strong>{summary?.unreadMessageCount ?? 0}</strong><span>okunmamış mesaj</span></div><em aria-hidden="true">→</em></Link>
          <section className="coach-dashboard__panel coach-dashboard__availability" aria-labelledby="availability-title"><div className="coach-dashboard__section-heading"><div><p>Takvim</p><h2 id="availability-title">Müsaitlik Saatleri</h2></div></div>{availabilityError ? <div className="coach-dashboard__empty">Müsaitlik saatleri alınamadı.</div> : openAvailability.length === 0 ? <div className="coach-dashboard__empty"><strong>Henüz müsaitlik saati eklenmemiş.</strong><span>Yeni bir saat tanımlandığında burada görünecek.</span></div> : <div className="coach-dashboard__availability-list">{openAvailability.map((slot) => <time dateTime={slot.startTime} key={slot.id}><span>{new Intl.DateTimeFormat("tr-TR", { weekday: "long", day: "numeric", month: "short" }).format(new Date(slot.startTime))}</span><strong>{formatRange(slot.startTime, slot.endTime)}</strong></time>)}</div>}</section>
        </div>
      </div>
    </div>
  );
}
