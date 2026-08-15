import React, { useEffect, useState } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";
import type { CoachDetailResponse, CoachSummaryResponse, PackageResponse, Track } from "../coaches/coachDiscoveryTypes";
import { TRACK_LABELS } from "../coaches/coachDiscoveryTypes";
import { CoachCard, StarIcon, ArrowRightIcon } from "../coaches/CoachCard";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import type { StudentDashboardResponse } from "../studentDashboard/studentDashboardTypes";
import { FormError } from "../components/FormError";
import { CheckoutSection } from "../subscriptionCheckout/CheckoutSection";
import { BookingSection } from "../booking/BookingSection";
import { TrialConsultationSection } from "../trial/TrialConsultationSection";
import { canMessageWithSubscription } from "../access/subscriptionAccess";
import { messagingApi } from "../messaging/messagingApi";
import { ReportModal } from "../safety/ReportModal";
import { ApiError } from "../api/ApiError";
import "../public/home-page.css";
import "../coaches/coach-list-page.css";
import "../coaches/coach-detail-page.css";

const SchoolIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
    <path d="m2 9 10-5 10 5-10 5-10-5Z" />
    <path d="M6 11v5c0 1.5 2.7 3 6 3s6-1.5 6-3v-5" />
  </svg>
);

const GroupIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="9" cy="8" r="3.2" />
    <path d="M2.5 19c.9-3.4 3.4-5.2 6.5-5.2s5.6 1.8 6.5 5.2M16 8.2a3 3 0 1 1 3.5 3M21.5 19c-.6-2.3-1.9-3.9-3.7-4.7" />
  </svg>
);

const CalendarStatIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3.5" y="5" width="17" height="16" rx="3" />
    <path d="M3.5 10h17M8 3v4M16 3v4" />
  </svg>
);

const LockIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <rect x="5" y="11" width="14" height="9" rx="2" />
    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
  </svg>
);

function coachInitials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase();
}

export const CoachDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const coachId = Number(id);

  const { user, isAuthenticated, isLoading: authLoading } = useAuth();
  const canView = isAuthenticated && (user?.role === "STUDENT" || user?.role === "ADMIN");

  const [coach, setCoach] = useState<CoachDetailResponse | null>(null);
  const [packages, setPackages] = useState<PackageResponse[]>([]);
  const [dashboardData, setDashboardData] = useState<StudentDashboardResponse | null>(null);
  const [similarCoaches, setSimilarCoaches] = useState<CoachSummaryResponse[]>([]);

  const [selectedPackage, setSelectedPackage] = useState<PackageResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<ApiError | Error | null>(null);

  const navigate = useNavigate();
  const [msgLoading, setMsgLoading] = useState<boolean>(false);
  const [showReportModal, setShowReportModal] = useState<boolean>(false);

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

  const sub = dashboardData?.subscription;
  const isSubscribedToThisCoach = sub?.coachId === coachId;
  const isMessageAllowed = isSubscribedToThisCoach && canMessageWithSubscription(sub?.status);

  useEffect(() => {
    if (!canView || !coachId) return;

    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError(null);
      try {
        const [coachDetail, packageList] = await Promise.all([
          coachDiscoveryApi.getCoachDetail(coachId),
          coachDiscoveryApi.listPackages(),
        ]);
        if (cancelled) return;
        setCoach(coachDetail);
        setPackages(packageList || []);

        if (user?.role === "STUDENT") {
          try {
            const dash = await studentDashboardApi.getDashboardData();
            if (!cancelled) setDashboardData(dash);
          } catch {
            // Gracefully continue without subscription info if it fails
          }
        }

        const firstTrack = coachDetail.tracks[0] as Track | undefined;
        try {
          const others = await coachDiscoveryApi.listCoaches({ track: firstTrack, size: 4 });
          if (!cancelled) setSimilarCoaches(others.content.filter((c) => c.id !== coachId).slice(0, 3));
        } catch {
          // Similar coaches are a nice-to-have; ignore failures silently.
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err : new Error("Koç bulunamadı."));
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void load();
    return () => {
      cancelled = true;
    };
  }, [coachId, user, canView]);

  if (authLoading) {
    return null;
  }

  if (!canView) {
    return (
      <div className="coaches-page">
        <div className="home-container">
          <div className="coaches-paywall">
            <div className="coaches-paywall__glow-a" />
            <div className="coaches-paywall__glow-b" />
            <div className="coaches-paywall__icon">
              <LockIcon />
            </div>
            <h2>Koç Profilini Görmek İçin Kayıt Ol!</h2>
            <p>
              {user?.role === "COACH"
                ? "Koç hesabınızla koç profillerine erişim bulunmuyor."
                : "Koçlarımızın profillerini, videolarını ve uygun görüşme saatlerini görmek için ücretsiz bir öğrenci hesabı oluşturun."}
            </p>
            {user?.role !== "COACH" && (
              <Link className="home-button home-button--primary" to="/register">
                Hemen Kayıt Ol
                <ArrowRightIcon />
              </Link>
            )}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="coach-detail-page">
      <div className="home-container">
        <Link className="coach-detail-back" to="/coaches">
          ← Koçlarımıza Geri Dön
        </Link>

        {loading ? (
          <div className="coaches-skeleton-grid" style={{ gridTemplateColumns: "1fr" }}>
            <div className="coaches-skeleton-card" style={{ height: 420 }} />
          </div>
        ) : error || !coach ? (
          <div className="coaches-error">
            <strong>Koç bulunamadı.</strong>
            <FormError error={error} />
          </div>
        ) : (
          <>
            <div className="coach-detail-grid">
              {/* Left column */}
              <div>
                <div className="coach-detail-header">
                  <div className="coach-detail-header__avatar">
                    {coach.profileImageUrl ? <img src={coach.profileImageUrl} alt={coach.fullName} /> : coachInitials(coach.fullName)}
                  </div>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12 }}>
                      <div>
                        <h1>{coach.fullName}</h1>
                        <p className="coach-detail-header__meta">
                          <SchoolIcon />
                          {coach.universityName}
                          {coach.department ? ` · ${coach.department}` : ""}
                        </p>
                      </div>
                      {user && user.id !== coach.userId && (
                        <button className="coach-detail-report" onClick={() => setShowReportModal(true)}>
                          Koçu Bildir
                        </button>
                      )}
                    </div>

                    <div className="coach-detail-header__badges">
                      <span className={`coach-detail-badge ${coach.acceptingNewStudents ? "coach-detail-badge--open" : "coach-detail-badge--full"}`}>
                        {coach.acceptingNewStudents ? "Öğrenci Alıyor" : "Kontenjan Dolu"}
                      </span>
                      {coach.tracks.map((track) => (
                        <span className="coach-detail-badge coach-detail-badge--track" key={track}>
                          {TRACK_LABELS[track as Track] ?? track}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="coach-detail-stats">
                  <div className="coach-detail-stat">
                    <StarIcon />
                    <strong>{coach.rating != null ? coach.rating.toFixed(1) : "Yeni"}</strong>
                    <span>Ortalama Puan</span>
                  </div>
                  <div className="coach-detail-stat">
                    <GroupIcon />
                    <strong>{coach.totalSessions}</strong>
                    <span>Tamamlanan Seans</span>
                  </div>
                  <div className="coach-detail-stat">
                    <CalendarStatIcon />
                    <strong>{coach.graduationYear ?? "—"}</strong>
                    <span>Mezuniyet Yılı</span>
                  </div>
                </div>

                <div className="coach-detail-section">
                  <h2>Hakkında</h2>
                  <p className="coach-detail-bio">{coach.bio || "Bu koç henüz bir biyografi eklememiş."}</p>
                </div>

                {user?.role === "STUDENT" && (
                  <div className="coach-detail-section">
                    <h2>Mesajlaş</h2>
                    {isMessageAllowed ? (
                      <button className="coaches-filter-submit" disabled={msgLoading} onClick={handleOpenConversation}>
                        {msgLoading ? "Sohbet Açılıyor…" : "Mesaj Gönder"}
                      </button>
                    ) : (
                      <p className="coach-detail-note">
                        Mesaj gönderebilmek için bu koç ile aktif veya geçmiş bir aboneliğiniz olmalı.
                      </p>
                    )}
                  </div>
                )}

                <div className="coach-detail-section">
                  <h2>Abonelik Paketleri</h2>
                  {packages.length === 0 ? (
                    <p className="coach-detail-note">Şu anda tanımlı bir paket bulunmuyor.</p>
                  ) : (
                    <div className="coach-detail-packages">
                      {packages.map((pkg) => {
                        const isSelected = selectedPackage?.id === pkg.id;
                        return (
                          <div
                            key={pkg.id}
                            className={`coach-detail-package${isSelected ? " coach-detail-package--selected" : ""}`}
                            onClick={() => setSelectedPackage(pkg)}
                          >
                            <div>
                              <h4>{pkg.name}</h4>
                              <div className="coach-detail-package__price">{pkg.price} TRY</div>
                              <div className="coach-detail-package__meta">
                                {pkg.durationDays} gün geçerlilik
                                <br />
                                Haftada {pkg.weeklySessions} görüşme
                              </div>
                            </div>
                            <button className="coach-detail-package__pick">{isSelected ? "Seçildi" : "Paket Seç"}</button>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {selectedPackage && (
                  <div className="coach-detail-section">
                    <div className="coach-detail-embed">
                      <CheckoutSection
                        coachId={coach.id}
                        coachName={coach.fullName}
                        packageId={selectedPackage.id}
                        packageName={selectedPackage.name}
                        price={selectedPackage.price}
                        dashboardData={dashboardData}
                      />
                    </div>
                  </div>
                )}

                {user?.role === "STUDENT" && (
                  <div className="coach-detail-section">
                    <h2>Seans Randevusu</h2>
                    <div className="coach-detail-embed">
                      <BookingSection coachId={coach.id} dashboardData={dashboardData} />
                    </div>
                  </div>
                )}
              </div>

              {/* Right column */}
              <div>
                <div className="coach-detail-video">
                  {coach.introVideoUrl ? (
                    <>
                      {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
                      <video src={coach.introVideoUrl} controls preload="metadata" />
                      <div className="coach-detail-video__caption">
                        <h3>Kendini Tanıt</h3>
                        <p>Bu kısa videoda koçunuzu daha yakından tanıyabilirsiniz.</p>
                      </div>
                    </>
                  ) : (
                    <div className="coach-detail-video__empty">Bu koç henüz bir tanıtım videosu eklememiş.</div>
                  )}
                </div>

                {user?.role === "STUDENT" && <TrialConsultationSection coachId={coach.id} />}
              </div>
            </div>
          </>
        )}
      </div>

      {!loading && !error && coach && similarCoaches.length > 0 && (
        <section className="coach-detail-similar">
          <div className="home-container">
            <h2>Diğer Koçlarımız</h2>
            <div className="coaches-grid">
              {similarCoaches.map((c) => (
                <CoachCard coach={c} key={c.id} />
              ))}
            </div>
          </div>
        </section>
      )}

      {showReportModal && coach && (
        <ReportModal targetType="USER" targetId={coach.userId} onClose={() => setShowReportModal(false)} />
      )}
    </div>
  );
};
