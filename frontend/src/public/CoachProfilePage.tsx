import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";
import type { CoachSummaryResponse, PublicCoachDetailResponse } from "../coaches/coachDiscoveryTypes";
import { useAuth } from "../auth/AuthProvider";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { canMessageWithSubscription } from "../access/subscriptionAccess";
import { messagingApi } from "../messaging/messagingApi";
import { safeYoutubeEmbedUrl, YouTubeEmbed } from "../coaches/YouTubeEmbed";
import "./coach-profile-page.css";

const trackLabels: Record<string, string> = {
  NUMERICAL: "Sayısal",
  EQUAL_WEIGHT: "Eşit Ağırlık",
  VERBAL: "Sözel",
  LANGUAGE: "Dil",
};

function initials(name: string) {
  return name.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toLocaleUpperCase("tr-TR");
}

function ProfileAvatar({ name, url, small = false }: { name: string; url: string | null; small?: boolean }) {
  return (
    <div className={small ? "coach-profile-avatar coach-profile-avatar--small" : "coach-profile-avatar"}>
      {url ? <img src={url} alt={`${name} profil fotoğrafı`} /> : <span aria-label={`${name} için profil fotoğrafı bulunmuyor`}>{initials(name)}</span>}
    </div>
  );
}

function SimilarCoachCard({ coach }: { coach: CoachSummaryResponse }) {
  return (
    <article className="coach-profile-similar-card">
      <ProfileAvatar name={coach.fullName} url={coach.profileImageUrl} small />
      <div>
        <h3>{coach.fullName}</h3>
        <p>{coach.universityName}</p>
        {coach.tracks?.[0] && <span>{trackLabels[coach.tracks[0]] ?? coach.tracks[0]}</span>}
      </div>
      <Link to={`/coaches/${coach.id}`} aria-label={`${coach.fullName} profilini incele`}>Profili İncele →</Link>
    </article>
  );
}

export function CoachProfilePage() {
  const { id } = useParams<{ id: string }>();
  const coachId = Number(id);
  const invalidCoachId = !Number.isInteger(coachId) || coachId <= 0;
  const [coach, setCoach] = useState<PublicCoachDetailResponse | null>(null);
  const [similar, setSimilar] = useState<CoachSummaryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);

  const { user } = useAuth();
  const navigate = useNavigate();
  const [dashboardData, setDashboardData] = useState<StudentDashboardResponse | null>(null);
  const [msgLoading, setMsgLoading] = useState(false);

  // Only a logged-in student can already be subscribed to this coach, so the "message" CTA
  // (below, in place of the public trial-consultation CTA) only applies to that case.
  useEffect(() => {
    if (user?.role !== "STUDENT") return;
    let active = true;
    studentDashboardApi
      .getDashboardData()
      .then((data) => { if (active) setDashboardData(data); })
      .catch(() => { /* Trial CTA remains the fallback if this fails. */ });
    return () => { active = false; };
  }, [user?.role]);

  const sub = dashboardData?.subscription;
  const isSubscribedToThisCoach = sub?.coachId === coachId;
  const isMessageAllowed = isSubscribedToThisCoach && canMessageWithSubscription(sub?.status);

  const handleOpenConversation = async () => {
    if (msgLoading) return;
    setMsgLoading(true);
    try {
      const response = await messagingApi.openConversation(coachId);
      navigate(`/messages/${response.id}`);
    } catch {
      alert("Mesajlaşma başlatılamadı.");
    } finally {
      setMsgLoading(false);
    }
  };

  const loadProfile = () => {
    if (!Number.isInteger(coachId) || coachId <= 0) {
      setErrorStatus(404);
      setLoading(false);
      return;
    }
    setLoading(true);
    setErrorStatus(null);
    coachDiscoveryApi.getPublicCoachDetail(coachId)
      .then((response) => {
        setCoach(response);
        document.title = `${response.fullName} | Uniform Akademi`;
        const track = response.tracks?.[0];
        if (!track) return;
        return coachDiscoveryApi.listCoaches(0, 4, { track, sort: "newest" })
          .then((page) => setSimilar((page.content ?? []).filter((item) => item.id !== coachId).slice(0, 3)))
          .catch(() => setSimilar([]));
      })
      .catch((error) => setErrorStatus(error instanceof ApiError ? error.status : 500))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    let active = true;
    if (invalidCoachId) return () => { active = false; };
    coachDiscoveryApi.getPublicCoachDetail(coachId)
      .then((response) => {
        if (!active) return;
        setCoach(response);
        document.title = `${response.fullName} | Uniform Akademi`;
        const track = response.tracks?.[0];
        if (!track) return;
        coachDiscoveryApi.listCoaches(0, 4, { track, sort: "newest" })
          .then((page) => { if (active) setSimilar((page.content ?? []).filter((item) => item.id !== coachId).slice(0, 3)); })
          .catch(() => { if (active) setSimilar([]); });
      })
      .catch((error) => { if (active) setErrorStatus(error instanceof ApiError ? error.status : 500); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [coachId, invalidCoachId]);

  if (!invalidCoachId && loading) {
    return <div className="coach-profile-state" aria-label="Koç profili yükleniyor" aria-busy="true"><span /><span /><span /></div>;
  }

  if (!coach || errorStatus || invalidCoachId) {
    const notFound = invalidCoachId || errorStatus === 404;
    return (
      <section className="coach-profile-error" role="alert">
        <h1>{notFound ? "Koç profili bulunamadı." : "Profil şu anda görüntülenemiyor."}</h1>
        <p>{notFound ? "Bu profil yayında olmayabilir veya kaldırılmış olabilir." : "Lütfen bağlantınızı kontrol edip yeniden deneyin."}</p>
        <div>
          {!notFound && <button type="button" onClick={loadProfile}>Yeniden Dene</button>}
          <Link to="/coaches">Koçlarımıza Dön</Link>
        </div>
      </section>
    );
  }

  const tracks = coach.tracks?.map((track) => trackLabels[track] ?? track) ?? [];

  return (
    <div className="coach-profile-page">
      <div className="coach-profile-container">
        <Link className="coach-profile-back" to="/coaches">← Koçlarımıza Dön</Link>
        <div className="coach-profile-grid">
          <section className="coach-profile-identity" aria-labelledby="coach-profile-title">
            <ProfileAvatar name={coach.fullName} url={coach.profileImageUrl} />
            <div>
              <h1 id="coach-profile-title">{coach.fullName}</h1>
              <p className="coach-profile-school">⌂ {[coach.universityName, coach.department].filter(Boolean).join(" · ")}</p>
              <p className="coach-profile-headline">{coach.headline}</p>
              <div className="coach-profile-badges">
                {tracks.map((track) => <span key={track}>{track}</span>)}
                <span className={coach.acceptingNewStudents ? "is-open" : "is-full"}>{coach.acceptingNewStudents ? "Yeni öğrenci kabul ediyor" : "Kontenjan dolu"}</span>
              </div>
            </div>
          </section>

          <section className="coach-profile-media" aria-labelledby="coach-profile-media-title">
            <div className="coach-profile-media__frame">
              {safeYoutubeEmbedUrl(coach.introVideoEmbedUrl) ? (
                <YouTubeEmbed url={coach.introVideoEmbedUrl} title={`${coach.fullName} tanıtım videosu`} />
              ) : (
                <div className="coach-profile-media__empty"><span aria-hidden="true">▷</span><p>Tanıtım videosu henüz eklenmedi.</p></div>
              )}
            </div>
            <h2 id="coach-profile-media-title">Kendini Tanıt</h2>
            <p>Bu kısa bölümde mentörünü daha yakından tanıyabilirsin.</p>
          </section>

          <section className="coach-profile-trial" aria-labelledby="coach-profile-trial-title">
            <h2 id="coach-profile-trial-title">Ücretsiz Tanışma Görüşmesi</h2>
            <p>Mentörünle tanışmak ve hedeflerini paylaşmak için hesabını oluştur. Uygun saatleri güvenli öğrenci akışında görüntüleyebilirsin.</p>
            <ul>
              <li><span aria-hidden="true">◷</span><div><strong>Görüşme şekli</strong><small>Çevrim içi</small></div></li>
              <li><span aria-hidden="true">⌛</span><div><strong>Süre</strong><small>20-30 dakika</small></div></li>
              <li><span aria-hidden="true">▣</span><div><strong>Uygun saatler</strong><small>Kayıt sonrası görüntülenir</small></div></li>
            </ul>
            {isSubscribedToThisCoach ? (
              isMessageAllowed ? (
                <button type="button" onClick={handleOpenConversation} disabled={msgLoading}>
                  {msgLoading ? "Sohbet Açılıyor…" : "Mesaj Gönder"}
                </button>
              ) : (
                <span className="coach-profile-trial__disabled" aria-disabled="true">
                  Mesaj gönderebilmek için aktif veya geçmiş bir aboneliğiniz olmalı
                </span>
              )
            ) : coach.acceptingNewStudents ? (
              <Link to={`/register?coachId=${coach.id}`}>Ücretsiz Görüşme İçin Kayıt Ol</Link>
            ) : (
              <span className="coach-profile-trial__disabled" aria-disabled="true">Kontenjan Şu Anda Dolu</span>
            )}
            {!isSubscribedToThisCoach && <small>Deneme görüşmesi ve rezervasyon işlemleri giriş gerektirir.</small>}
          </section>

          <section className="coach-profile-about" aria-labelledby="coach-profile-about-title">
            <h2 id="coach-profile-about-title">Hakkında</h2>
            <p>{coach.bio || "Mentör henüz bir biyografi eklemedi."}</p>
          </section>

          <section className="coach-profile-stats" aria-label="Koç istatistikleri">
            {coach.rating != null && <article><span aria-hidden="true">★</span><strong>{coach.rating.toFixed(1)}</strong><small>Ortalama Puan</small></article>}
            <article><span aria-hidden="true">♧</span><strong>{coach.totalSessions}</strong><small>Tamamlanan Görüşme</small></article>
            {coach.graduationYear && <article><span aria-hidden="true">▣</span><strong>{coach.graduationYear}</strong><small>Mezuniyet Yılı</small></article>}
          </section>
        </div>
      </div>

      <section className="coach-profile-similar" aria-labelledby="coach-profile-similar-title">
        <div className="coach-profile-container">
          <h2 id="coach-profile-similar-title">Benzer Mentörler</h2>
          {similar.length > 0 ? (
            <div className="coach-profile-similar-grid">{similar.map((item) => <SimilarCoachCard key={item.id} coach={item} />)}</div>
          ) : (
            <p className="coach-profile-similar__empty">Aynı alanda başka aktif mentör bulunmuyor.</p>
          )}
        </div>
      </section>
    </div>
  );
}
