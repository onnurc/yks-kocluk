/* eslint-disable react-hooks/set-state-in-effect -- these effects initiate and reset independent API sections */
import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { bookingApi } from "../booking/bookingApi";
import type { SessionResponse } from "../booking/bookingTypes";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";
import type { PublicCoachDetailResponse } from "../coaches/coachDiscoveryTypes";
import { messagingApi } from "../messaging/messagingApi";
import type { ConversationResponse } from "../messaging/messagingTypes";
import { MESSAGE_NOTIFICATION_EVENT } from "../messaging/useNotificationSocket";
import { CoachDashboardPage } from "./CoachDashboardPage";
import { ActiveSubscriptionCard } from "../studentDashboard/ActiveSubscriptionCard";
import { PendingPaymentWarning } from "../studentDashboard/PendingPaymentWarning";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type {
  DashboardSubscription,
  StudentDashboardResponse,
  SubscriptionStatus,
} from "../studentDashboard/studentDashboardTypes";
import "./student-dashboard.css";

const liveSubscriptionStatuses: SubscriptionStatus[] = ["ACTIVE", "PAST_DUE"];

const subscriptionStatusLabels: Record<SubscriptionStatus, string> = {
  PENDING_PAYMENT: "Ödeme bekleniyor",
  ACTIVE: "Aktif",
  CANCELLED: "İptal edildi",
  PAST_DUE: "Ödeme gecikmiş",
  TERMINATED: "Sonlandırıldı",
  EXPIRED: "Süresi doldu",
};

const sessionStatusLabels: Record<SessionResponse["status"], string> = {
  PLANNED: "Planlandı",
  COMPLETED: "Tamamlandı",
  CANCELLED: "İptal edildi",
  LATE_CANCELLED: "Geç iptal",
  NO_SHOW: "Katılmadı",
};

const isLiveSubscription = (subscription: DashboardSubscription | null | undefined) =>
  Boolean(subscription && liveSubscriptionStatuses.includes(subscription.status));

const initials = (name: string) =>
  name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toLocaleUpperCase("tr-TR");

const firstName = (fullName: string) => fullName.trim().split(/\s+/)[0] || fullName;

const formatMeetingDate = (value: string) =>
  new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "long" }).format(new Date(value));

const formatMeetingTime = (start: string, end: string) => {
  const formatter = new Intl.DateTimeFormat("tr-TR", { hour: "2-digit", minute: "2-digit" });
  return `${formatter.format(new Date(start))} – ${formatter.format(new Date(end))}`;
};

const formatMessageTime = (value: string) => {
  const date = new Date(value);
  const now = new Date();
  const sameDay = date.toDateString() === now.toDateString();
  if (sameDay) {
    return new Intl.DateTimeFormat("tr-TR", { hour: "2-digit", minute: "2-digit" }).format(date);
  }
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return "Dün";
  return new Intl.DateTimeFormat("tr-TR", { day: "numeric", month: "short" }).format(date);
};

function MessageIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M5 5.5h14v10H9l-4 3v-13Z" />
      <path d="M8 9h8M8 12h6" />
    </svg>
  );
}

function ClockIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="8.5" />
      <path d="M12 7.5V12l3 2" />
    </svg>
  );
}

function VideoIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <rect x="3.5" y="6" width="11.5" height="12" rx="2" />
      <path d="m15 10 5-2v8l-5-2" />
    </svg>
  );
}

function CoachAvatar({ name, imageUrl }: { name: string; imageUrl?: string | null }) {
  const [imageFailed, setImageFailed] = useState(false);

  return (
    <span className="student-dashboard__avatar" aria-label={`${name} profil görseli`}>
      {imageUrl && !imageFailed ? (
        <img src={imageUrl} alt="" onError={() => setImageFailed(true)} />
      ) : (
        <span>{initials(name)}</span>
      )}
    </span>
  );
}

function SectionError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="student-dashboard__section-error" role="alert">
      <p>{message}</p>
      <button type="button" onClick={onRetry}>Yeniden dene</button>
    </div>
  );
}

export const DashboardPage = () => {
  const { user, isSuspended } = useAuth();
  const navigate = useNavigate();
  const isStudent = user?.role === "STUDENT";

  const [dashboard, setDashboard] = useState<StudentDashboardResponse | null>(null);
  const [coach, setCoach] = useState<PublicCoachDetailResponse | null>(null);
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [sessions, setSessions] = useState<SessionResponse[]>([]);
  const [dashboardLoading, setDashboardLoading] = useState(false);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [coachLoading, setCoachLoading] = useState(false);
  const [dashboardError, setDashboardError] = useState(false);
  const [messagesError, setMessagesError] = useState(false);
  const [sessionsError, setSessionsError] = useState(false);
  const [dashboardVersion, setDashboardVersion] = useState(0);
  const [sessionsVersion, setSessionsVersion] = useState(0);
  const [showSubscriptionDetails, setShowSubscriptionDetails] = useState(false);
  const [dashboardLoadedAt] = useState(Date.now);

  useEffect(() => {
    if (isSuspended) {
      navigate("/suspended", { replace: true });
    } else if (user?.role === "ADMIN") {
      navigate("/admin", { replace: true });
    }
  }, [isSuspended, navigate, user?.role]);

  useEffect(() => {
    if (!isStudent) return;
    let active = true;
    setDashboardLoading(true);
    setDashboardError(false);
    studentDashboardApi
      .getDashboardData()
      .then((response) => {
        if (active) setDashboard(response);
      })
      .catch(() => {
        if (active) {
          setDashboard(null);
          setDashboardError(true);
        }
      })
      .finally(() => {
        if (active) setDashboardLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dashboardVersion, isStudent]);

  useEffect(() => {
    const subscription = dashboard?.subscription;
    if (!isLiveSubscription(subscription)) {
      setCoach(null);
      setCoachLoading(false);
      return;
    }
    let active = true;
    setCoachLoading(true);
    coachDiscoveryApi
      .getPublicCoachDetail(subscription!.coachId)
      .then((response) => {
        if (active) setCoach(response);
      })
      .catch(() => {
        if (active) setCoach(null);
      })
      .finally(() => {
        if (active) setCoachLoading(false);
      });
    return () => {
      active = false;
    };
  }, [dashboard?.subscription]);

  useEffect(() => {
    if (!isStudent) return;
    let active = true;

    const loadConversations = (silent = false) => {
      if (!silent) setMessagesLoading(true);
      setMessagesError(false);
      return messagingApi
        .listConversations()
        .then((response) => {
          if (active) setConversations(response ?? []);
        })
        .catch(() => {
          if (active) setMessagesError(true);
        })
        .finally(() => {
          if (active && !silent) setMessagesLoading(false);
        });
    };

    void loadConversations();
    const refresh = () => void loadConversations(true);
    window.addEventListener(MESSAGE_NOTIFICATION_EVENT, refresh);
    window.addEventListener("messages-read", refresh);
    return () => {
      active = false;
      window.removeEventListener(MESSAGE_NOTIFICATION_EVENT, refresh);
      window.removeEventListener("messages-read", refresh);
    };
  }, [isStudent]);

  useEffect(() => {
    if (!isStudent) return;
    let active = true;
    setSessionsLoading(true);
    setSessionsError(false);
    bookingApi
      .listMySessions()
      .then((response) => {
        if (active) setSessions(response ?? []);
      })
      .catch(() => {
        if (active) setSessionsError(true);
      })
      .finally(() => {
        if (active) setSessionsLoading(false);
      });
    return () => {
      active = false;
    };
  }, [isStudent, sessionsVersion]);

  const unreadCount = useMemo(
    () => conversations.reduce((total, conversation) => total + (conversation.unreadCount || 0), 0),
    [conversations]
  );

  const recentConversations = useMemo(
    () => conversations.filter((conversation) => conversation.lastMessage && conversation.lastMessageAt).slice(0, 3),
    [conversations]
  );

  const upcomingSessions = useMemo(
    () =>
      sessions
        .filter((session) => session.status === "PLANNED" && new Date(session.startTime).getTime() > dashboardLoadedAt)
        .sort((left, right) => new Date(left.startTime).getTime() - new Date(right.startTime).getTime())
        .slice(0, 3),
    [dashboardLoadedAt, sessions]
  );

  if (!user) {
    return <div className="student-dashboard__route-state" aria-busy="true">Panel hazırlanıyor…</div>;
  }

  if (user.role === "ADMIN") {
    return <div className="student-dashboard__route-state" aria-label="Admin paneline yönlendiriliyor" />;
  }

  if (user.role === "COACH") {
    return <CoachDashboardPage />;
  }

  const subscription = dashboard?.subscription ?? null;
  const hasLiveSubscription = isLiveSubscription(subscription);
  const coachName = coach?.fullName || subscription?.coachName || "";
  const coachMeta = [coach?.universityName, coach?.department].filter(Boolean).join(" · ");
  const showDashboardSkeleton = dashboardLoading && !dashboard;

  return (
    <div className="student-dashboard" aria-busy={dashboardLoading || messagesLoading || sessionsLoading}>
      <header className="student-dashboard__welcome">
        <p>Tekrar hoş geldin, {firstName(user.fullName)}.</p>
        <h1>Yolculuğuna kaldığın yerden devam et.</h1>
      </header>

      <section className="student-dashboard__summary" aria-label="Öğrenci özeti">
        <article className="student-dashboard__summary-card student-dashboard__coach-card" aria-label="Aktif koç">
          <p className="student-dashboard__eyebrow">Aktif Koç</p>
          {showDashboardSkeleton ? (
            <div className="student-dashboard__skeleton" aria-label="Aktif koç yükleniyor" />
          ) : dashboardError ? (
            <SectionError message="Koç bilginiz şu anda alınamadı." onRetry={() => setDashboardVersion((value) => value + 1)} />
          ) : hasLiveSubscription && coachName ? (
            <>
              <div className="student-dashboard__coach-identity">
                <CoachAvatar key={coach?.profileImageUrl ?? coachName} name={coachName} imageUrl={coach?.profileImageUrl} />
                <div>
                  <h2>{coachName}</h2>
                  <p>{coachLoading ? "Profil bilgisi yükleniyor…" : coachMeta || "Aktif mentorluk ilişkiniz"}</p>
                </div>
              </div>
              <Link className="student-dashboard__card-action" to={`/coaches/${subscription!.coachId}`}>
                Profili gör <span aria-hidden="true">→</span>
              </Link>
            </>
          ) : (
            <div className="student-dashboard__empty-summary">
              <span className="student-dashboard__empty-icon" aria-hidden="true">+</span>
              <h2>Aktif koçunuz yok</h2>
              <p>Size uygun mentörü gerçek koç listemizden keşfedebilirsiniz.</p>
              <Link className="student-dashboard__card-action" to="/coaches">Koçları keşfet</Link>
            </div>
          )}
        </article>

        <article className="student-dashboard__summary-card student-dashboard__subscription-card" aria-label="Abonelik">
          <div className="student-dashboard__summary-heading">
            <p className="student-dashboard__eyebrow">Abonelik</p>
            {subscription && <span className={`student-dashboard__status student-dashboard__status--${subscription.status.toLowerCase()}`}>{subscriptionStatusLabels[subscription.status]}</span>}
          </div>
          {showDashboardSkeleton ? (
            <div className="student-dashboard__skeleton" aria-label="Abonelik yükleniyor" />
          ) : dashboardError ? (
            <SectionError message="Abonelik bilginiz şu anda alınamadı." onRetry={() => setDashboardVersion((value) => value + 1)} />
          ) : subscription ? (
            <>
              <div className="student-dashboard__subscription-mark" aria-hidden="true">✦</div>
              <h2>{subscription.packageName}</h2>
              <p>{subscriptionStatusLabels[subscription.status]}</p>
              {(hasLiveSubscription || subscription.status === "PENDING_PAYMENT") && (
                <button
                  className="student-dashboard__card-action"
                  type="button"
                  aria-expanded={showSubscriptionDetails}
                  onClick={() => setShowSubscriptionDetails((visible) => !visible)}
                >
                  {hasLiveSubscription ? "Aboneliği yönet" : "Ödeme durumunu gör"}
                </button>
              )}
            </>
          ) : (
            <div className="student-dashboard__empty-summary">
              <div className="student-dashboard__subscription-mark" aria-hidden="true">✦</div>
              <h2>Aktif abonelik yok</h2>
              <p>Abonelik başlatmak için mevcut koç profillerini inceleyebilirsiniz.</p>
              <Link className="student-dashboard__card-action" to="/coaches">Koçları keşfet</Link>
            </div>
          )}
        </article>

        <Link className="student-dashboard__messages-card" to="/messages" aria-label={`${unreadCount} okunmamış mesaj, Mesajlara git`}>
          <div className="student-dashboard__messages-heading">
            <span className="student-dashboard__message-icon"><MessageIcon /></span>
            <h2>Mesajlar</h2>
          </div>
          {messagesLoading && conversations.length === 0 ? (
            <p>Mesaj özeti yükleniyor…</p>
          ) : messagesError && conversations.length === 0 ? (
            <p>Mesaj bilginiz şu anda alınamadı.</p>
          ) : (
            <div className="student-dashboard__unread">
              <span>Okunmamış bildirim</span>
              <strong>{unreadCount}</strong>
              <em>yeni mesaj</em>
            </div>
          )}
          <span className="student-dashboard__messages-arrow" aria-hidden="true">→</span>
        </Link>
      </section>

      {subscription?.status === "PENDING_PAYMENT" && showSubscriptionDetails && (
        <section className="student-dashboard__subscription-details" aria-label="Ödeme durumu">
          <PendingPaymentWarning payment={dashboard?.payment ?? null} onRefresh={() => setDashboardVersion((value) => value + 1)} />
        </section>
      )}

      {hasLiveSubscription && subscription && showSubscriptionDetails && (
        <section className="student-dashboard__subscription-details" aria-label="Abonelik yönetimi">
          <ActiveSubscriptionCard subscription={subscription} payment={dashboard?.payment ?? null} onRefresh={() => setDashboardVersion((value) => value + 1)} />
        </section>
      )}

      <div className="student-dashboard__content-grid">
        <section className="student-dashboard__section" aria-labelledby="upcoming-meetings-title">
          <div className="student-dashboard__section-heading">
            <h2 id="upcoming-meetings-title">Yaklaşan Görüşmeler</h2>
            <Link to="/bookings">Tümünü gör <span aria-hidden="true">→</span></Link>
          </div>
          {sessionsLoading ? (
            <div className="student-dashboard__list-state" aria-label="Yaklaşan görüşmeler yükleniyor">Görüşmeler yükleniyor…</div>
          ) : sessionsError ? (
            <SectionError message="Görüşmeleriniz şu anda alınamadı." onRetry={() => setSessionsVersion((value) => value + 1)} />
          ) : upcomingSessions.length === 0 ? (
            <div className="student-dashboard__list-state">
              <strong>Yaklaşan görüşmeniz yok.</strong>
              <span>Yeni bir görüşme planlandığında burada göreceksiniz.</span>
            </div>
          ) : (
            <div className="student-dashboard__meeting-list">
              {upcomingSessions.map((session) => (
                <article className="student-dashboard__meeting" key={session.id}>
                  <time className="student-dashboard__date-tile" dateTime={session.startTime}>
                    <span>{new Intl.DateTimeFormat("tr-TR", { month: "short" }).format(new Date(session.startTime)).replace(".", "")}</span>
                    <strong>{new Intl.DateTimeFormat("tr-TR", { day: "2-digit" }).format(new Date(session.startTime))}</strong>
                  </time>
                  <div className="student-dashboard__meeting-body">
                    <h3>{session.coachName}</h3>
                    <p className="student-dashboard__meeting-date">{formatMeetingDate(session.startTime)}</p>
                    <p><ClockIcon /> {formatMeetingTime(session.startTime, session.endTime)}</p>
                  </div>
                  <span className="student-dashboard__meeting-status">{sessionStatusLabels[session.status]}</span>
                  {session.meetLink ? (
                    <a
                      className="student-dashboard__join"
                      href={session.meetLink}
                      target="_blank"
                      rel="noreferrer"
                      aria-label={`${session.coachName} ile görüşmeye katıl`}
                    >
                      <VideoIcon />
                    </a>
                  ) : (
                    <span className="student-dashboard__join student-dashboard__join--disabled" title="Görüşme bağlantısı henüz hazır değil" aria-label="Görüşme bağlantısı henüz hazır değil">
                      <VideoIcon />
                    </span>
                  )}
                </article>
              ))}
            </div>
          )}
        </section>

        <section className="student-dashboard__section student-dashboard__recent" aria-labelledby="recent-messages-title">
          <div className="student-dashboard__section-heading">
            <h2 id="recent-messages-title">Son Mesajlar</h2>
            <Link to="/messages">Tümünü gör <span aria-hidden="true">→</span></Link>
          </div>
          {messagesLoading ? (
            <div className="student-dashboard__list-state" aria-label="Son mesajlar yükleniyor">Mesajlar yükleniyor…</div>
          ) : messagesError ? (
            <SectionError message="Son mesajlarınız şu anda alınamadı." onRetry={() => window.dispatchEvent(new Event("messages-read"))} />
          ) : recentConversations.length === 0 ? (
            <div className="student-dashboard__list-state">
              <strong>Henüz mesajınız yok.</strong>
              <span>Koçunuzla yazışmaya başladığınızda son mesajlar burada görünecek.</span>
            </div>
          ) : (
            <div className="student-dashboard__recent-list">
              {recentConversations.map((conversation) => (
                <Link className="student-dashboard__recent-message" to={`/messages/${conversation.id}`} key={conversation.id}>
                  <CoachAvatar name={conversation.coachName} />
                  <div>
                    <span className="student-dashboard__recent-heading">
                      <strong>{conversation.coachName}</strong>
                      <time dateTime={conversation.lastMessageAt!}>{formatMessageTime(conversation.lastMessageAt!)}</time>
                    </span>
                    <p title={conversation.lastMessage!}>{conversation.lastMessage}</p>
                  </div>
                  {conversation.unreadCount > 0 && <span className="student-dashboard__recent-unread" aria-label={`${conversation.unreadCount} okunmamış mesaj`}>{conversation.unreadCount}</span>}
                </Link>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
};
