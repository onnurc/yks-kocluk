import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";
import type { CoachSummaryResponse, PackageResponse, PublicCoachDetailResponse } from "../coaches/coachDiscoveryTypes";
import { useAuth } from "../auth/AuthProvider";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { canMessageWithSubscription } from "../access/subscriptionAccess";
import { messagingApi } from "../messaging/messagingApi";
import { safeYoutubeEmbedUrl, YouTubeEmbed } from "../coaches/YouTubeEmbed";
import { CheckoutSection } from "../subscriptionCheckout/CheckoutSection";
import { BookingSection } from "../booking/BookingSection";
import { TrialConsultationSection } from "../trial/TrialConsultationSection";
import { ReportModal } from "../safety/ReportModal";
import "./coach-profile-page.css";

const trackLabels: Record<string, string> = {
  NUMERICAL: "Sayısal",
  EQUAL_WEIGHT: "Eşit Ağırlık",
  VERBAL: "Sözel",
  LANGUAGE: "Dil",
};

type LoadState = "loading" | "ready" | "error";

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
  const [dashboardResult, setDashboardResult] = useState<{ userId: number; state: LoadState; data: StudentDashboardResponse | null } | null>(null);
  const [packageResult, setPackageResult] = useState<{ userId: number; state: LoadState; data: PackageResponse[] } | null>(null);
  const [packageSelection, setPackageSelection] = useState<{ coachId: number; value: PackageResponse } | null>(null);
  const [reportTarget, setReportTarget] = useState<{ coachId: number; viewerId: number; targetUserId: number } | null>(null);
  const [showReportModal, setShowReportModal] = useState(false);
  const [msgLoading, setMsgLoading] = useState(false);

  useEffect(() => {
    if (user?.role !== "STUDENT") return;
    let active = true;
    const userId = user.id;
    studentDashboardApi
      .getDashboardData()
      .then((data) => {
        if (!active) return;
        setDashboardResult({ userId, state: "ready", data });
      })
      .catch(() => { if (active) setDashboardResult({ userId, state: "error", data: null }); });
    coachDiscoveryApi
      .listPackages()
      .then((items) => {
        if (!active) return;
        setPackageResult({ userId, state: "ready", data: items ?? [] });
      })
      .catch(() => { if (active) setPackageResult({ userId, state: "error", data: [] }); });
    return () => { active = false; };
  }, [user?.id, user?.role]);

  useEffect(() => {
    if ((user?.role !== "STUDENT" && user?.role !== "ADMIN") || invalidCoachId) return;
    let active = true;
    coachDiscoveryApi.getCoachDetail(coachId)
      .then((detail) => {
        if (active && detail.userId !== user.id) {
          setReportTarget({ coachId, viewerId: user.id, targetUserId: detail.userId });
        }
      })
      .catch(() => { /* Reporting remains hidden if the protected identity lookup is unavailable. */ });
    return () => { active = false; };
  }, [coachId, invalidCoachId, user?.id, user?.role]);

  const studentResultMatches = user?.role === "STUDENT" && dashboardResult?.userId === user.id;
  const packageResultMatches = user?.role === "STUDENT" && packageResult?.userId === user.id;
  const dashboardData = studentResultMatches ? dashboardResult.data : null;
  const dashboardState: LoadState = studentResultMatches ? dashboardResult.state : "loading";
  const packages = packageResultMatches ? packageResult.data : [];
  const packagesState: LoadState = packageResultMatches ? packageResult.state : "loading";
  const selectedPackage = packageSelection?.coachId === coachId ? packageSelection.value : null;
  const reportTargetUserId = reportTarget?.coachId === coachId && reportTarget.viewerId === user?.id
    ? reportTarget.targetUserId
    : null;

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
              {reportTargetUserId && (
                <button className="coach-profile-report" type="button" onClick={() => setShowReportModal(true)}>
                  Koçu Bildir
                </button>
              )}
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

          <section className="coach-profile-trial" aria-label="Ücretsiz Tanışma Görüşmesi">
            {user?.role === "STUDENT" && !isSubscribedToThisCoach ? (
              <TrialConsultationSection coachId={coach.id} />
            ) : (
              <>
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
                ) : !user ? (
                  coach.acceptingNewStudents ? (
                    <Link to={`/register?coachId=${coach.id}`}>Ücretsiz Görüşme İçin Kayıt Ol</Link>
                  ) : (
                    <span className="coach-profile-trial__disabled" aria-disabled="true">Kontenjan Şu Anda Dolu</span>
                  )
                ) : (
                  <span className="coach-profile-trial__disabled" aria-disabled="true">
                    Görüşme talebi yalnızca öğrenci hesaplarıyla oluşturulabilir
                  </span>
                )}
                {!user && <small>Deneme görüşmesi ve rezervasyon işlemleri giriş gerektirir.</small>}
              </>
            )}
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

        {user?.role === "STUDENT" && (
          <section className="coach-profile-commerce" aria-labelledby="coach-profile-packages-title">
            <div className="coach-profile-section-heading">
              <span>Koçluk planını seç</span>
              <h2 id="coach-profile-packages-title">Abonelik Paketleri</h2>
              <p>İhtiyacına uygun paketi seç; sözleşme ve güvenli İyzico adımları mevcut ödeme akışı üzerinden tamamlanır.</p>
            </div>

            {packagesState === "loading" && <p className="coach-profile-product-state" role="status">Paketler yükleniyor…</p>}
            {packagesState === "error" && <p className="coach-profile-product-state" role="alert">Paketler şu anda yüklenemiyor. Lütfen daha sonra tekrar deneyin.</p>}
            {packagesState === "ready" && packages.length === 0 && <p className="coach-profile-product-state">Şu anda aktif bir paket bulunmuyor.</p>}
            {dashboardState === "error" && (
              <p className="coach-profile-product-state coach-profile-product-state--warning" role="alert">
                Abonelik durumunuz doğrulanamadığı için yeni ödeme başlatılamıyor. Lütfen sayfayı yenileyin.
              </p>
            )}

            {packages.length > 0 && (
              <div className="coach-profile-packages">
                {packages.map((pkg) => {
                  const selected = selectedPackage?.id === pkg.id;
                  const unavailable = dashboardState !== "ready" || !coach.acceptingNewStudents;
                  return (
                    <article className={`coach-profile-package${selected ? " coach-profile-package--selected" : ""}`} key={pkg.id}>
                      <h3>{pkg.name}</h3>
                      <strong>{pkg.price.toLocaleString("tr-TR")} TRY</strong>
                      <p>{pkg.durationDays} gün · Haftada {pkg.weeklySessions} görüşme</p>
                      <button type="button" disabled={unavailable} onClick={() => setPackageSelection({ coachId, value: pkg })}>
                        {selected ? "Seçildi" : "Paketi Seç"}
                      </button>
                    </article>
                  );
                })}
              </div>
            )}

            {!coach.acceptingNewStudents && (
              <p className="coach-profile-product-state coach-profile-product-state--warning">Bu koçun kontenjanı dolu olduğu için yeni satın alım başlatılamaz.</p>
            )}

            {selectedPackage && dashboardState === "ready" && coach.acceptingNewStudents && (
              <div className="coach-profile-checkout">
                <CheckoutSection
                  coachId={coach.id}
                  coachName={coach.fullName}
                  packageId={selectedPackage.id}
                  packageName={selectedPackage.name}
                  price={selectedPackage.price}
                  dashboardData={dashboardData}
                />
              </div>
            )}
          </section>
        )}

        {user?.role === "STUDENT" && isSubscribedToThisCoach && (
          <section className="coach-profile-booking" aria-labelledby="coach-profile-booking-title">
            <h2 id="coach-profile-booking-title">Seans Randevusu</h2>
            <BookingSection coachId={coach.id} dashboardData={dashboardData} />
          </section>
        )}
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

      {showReportModal && reportTargetUserId && (
        <ReportModal targetType="USER" targetId={reportTargetUserId} onClose={() => setShowReportModal(false)} />
      )}
    </div>
  );
}
