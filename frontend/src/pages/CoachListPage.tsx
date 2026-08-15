import React, { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";
import type { CoachSearchParams } from "../coaches/coachDiscoveryApi";
import type { CoachSummaryResponse, Track } from "../coaches/coachDiscoveryTypes";
import { TRACK_LABELS } from "../coaches/coachDiscoveryTypes";
import { CoachCard, ArrowRightIcon } from "../coaches/CoachCard";
import { ApiError } from "../api/ApiError";
import "../public/home-page.css";
import "../coaches/coach-list-page.css";

const TRACKS: Track[] = ["NUMERICAL", "EQUAL_WEIGHT", "VERBAL", "LANGUAGE"];

const TRACK_ICONS: Record<Track, React.FC> = {
  NUMERICAL: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 3v5.2L4.5 18a2 2 0 0 0 1.8 3h11.4a2 2 0 0 0 1.8-3L15 8.2V3" />
      <path d="M9 3h6M6.5 14h11" />
    </svg>
  ),
  EQUAL_WEIGHT: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3v18M7 8 3 16h8L7 8Zm10 0-4 8h8l-4-8ZM4 20h16" />
    </svg>
  ),
  VERBAL: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 5c3-1.2 6-1.2 8 0v14c-2-1.2-5-1.2-8 0V5ZM20 5c-3-1.2-6-1.2-8 0v14c2-1.2 5-1.2 8 0V5Z" />
    </svg>
  ),
  LANGUAGE: () => (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="9" />
      <path d="M3 12h18M12 3a14 14 0 0 1 0 18 14 14 0 0 1 0-18Z" />
    </svg>
  ),
};

const SearchIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="11" cy="11" r="7" />
    <path d="m20 20-3.5-3.5" />
  </svg>
);

const ChevronDownIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="m6 9 6 6 6-6" />
  </svg>
);

const TuneIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 6h9M17 6h3M4 12h3M9 12h11M4 18h13M19 18h1" />
    <circle cx="13" cy="6" r="2" />
    <circle cx="7" cy="12" r="2" />
    <circle cx="17" cy="18" r="2" />
  </svg>
);

const LockIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <rect x="5" y="11" width="14" height="9" rx="2" />
    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
  </svg>
);

export const CoachListPage: React.FC = () => {
  const { user, isAuthenticated, isLoading: authLoading } = useAuth();
  const canBrowse = isAuthenticated && (user?.role === "STUDENT" || user?.role === "ADMIN");

  const [coaches, setCoaches] = useState<CoachSummaryResponse[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<ApiError | Error | null>(null);

  const [searchInput, setSearchInput] = useState("");
  const [q, setQ] = useState("");
  const [track, setTrack] = useState<Track | "">("");
  const [sort, setSort] = useState<"createdAt,desc" | "createdAt,asc">("createdAt,desc");

  const fetchCoaches = useCallback(
    async (targetPage: number, append: boolean) => {
      append ? setLoadingMore(true) : setLoading(true);
      setError(null);
      try {
        const params: CoachSearchParams = { page: targetPage, size: 9, sort };
        if (track) params.track = track;
        if (q.trim()) params.q = q.trim();
        const response = await coachDiscoveryApi.listCoaches(params);
        setCoaches((prev) => (append ? [...prev, ...response.content] : response.content));
        setTotalElements(response.totalElements);
        setLast(response.last);
        setPage(response.page);
      } catch (err) {
        setError(err instanceof Error ? err : new Error("Koçlar yüklenemedi."));
      } finally {
        append ? setLoadingMore(false) : setLoading(false);
      }
    },
    [track, q, sort],
  );

  useEffect(() => {
    if (!canBrowse) return;
    void fetchCoaches(0, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canBrowse, track, q, sort]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setQ(searchInput);
  };

  const toggleTrackChip = (value: Track) => {
    setTrack((current) => (current === value ? "" : value));
  };

  return (
    <div className="coaches-page">
      <div className="home-container">
        <section className="coaches-hero">
          <div className="home-eyebrow">
            <span aria-hidden="true" />
            Geleceğini Tasarla
          </div>
          <h1>
            Hayalindeki Üniversiteye Giden Yolda,
            <br />
            <em>
              En Doğru Rehberi Bul.
              <svg className="coaches-hero__squiggle" viewBox="0 0 200 9" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M2 6.64C47.78 1.95 128.52-1.41 198 6.64" stroke="currentColor" strokeWidth="3" strokeLinecap="round" />
              </svg>
            </em>
          </h1>
          <p>
            Türkiye&apos;nin seçkin üniversitelerinde okuyan koçlarımızla tanışın. Size en uygun koçu bulmak için
            filtreleri kullanın.
          </p>
        </section>

        {canBrowse ? (
          <>
            <div className="coaches-filters">
              <form className="coaches-filters__row" onSubmit={handleSearchSubmit}>
                <div className="coaches-search">
                  <SearchIcon />
                  <input
                    type="text"
                    placeholder="Koç adı veya uzmanlık alanı ara…"
                    value={searchInput}
                    onChange={(e) => setSearchInput(e.target.value)}
                  />
                </div>

                <div className="coaches-select">
                  <span className="coaches-select__label">Alan</span>
                  <select value={track} onChange={(e) => setTrack(e.target.value as Track | "")}>
                    <option value="">Tümü</option>
                    {TRACKS.map((t) => (
                      <option key={t} value={t}>
                        {TRACK_LABELS[t]}
                      </option>
                    ))}
                  </select>
                  <ChevronDownIcon />
                </div>

                <div className="coaches-select">
                  <span className="coaches-select__label">Sıralama</span>
                  <select value={sort} onChange={(e) => setSort(e.target.value as typeof sort)}>
                    <option value="createdAt,desc">En Yeni</option>
                    <option value="createdAt,asc">En Eski</option>
                  </select>
                  <ChevronDownIcon />
                </div>

                <button className="coaches-filter-submit" type="submit">
                  <TuneIcon />
                  Filtrele
                </button>
              </form>

              <div className="coaches-chips">
                {TRACKS.map((t) => {
                  const Icon = TRACK_ICONS[t];
                  return (
                    <button
                      key={t}
                      type="button"
                      className={`coaches-chip${track === t ? " coaches-chip--active" : ""}`}
                      onClick={() => toggleTrackChip(t)}
                    >
                      <Icon />
                      {TRACK_LABELS[t]}
                    </button>
                  );
                })}
              </div>
            </div>

            {!loading && !error && coaches.length > 0 && (
              <p className="coaches-results-meta">{totalElements} koç bulundu</p>
            )}

            {loading ? (
              <div className="coaches-skeleton-grid">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div className="coaches-skeleton-card" key={i} />
                ))}
              </div>
            ) : error ? (
              <div className="coaches-error">
                <strong>Koçlar yüklenirken bir sorun oluştu.</strong>
                <p>{error instanceof ApiError ? error.detail || error.title : error.message}</p>
                <button onClick={() => fetchCoaches(0, false)}>Yeniden Dene</button>
              </div>
            ) : coaches.length === 0 ? (
              <div className="coaches-empty">
                <p>Bu kriterlere uyan koç bulunamadı. Filtreleri değiştirip tekrar deneyin.</p>
              </div>
            ) : (
              <>
                <div className="coaches-grid">
                  {coaches.map((coach) => (
                    <CoachCard coach={coach} key={coach.id} />
                  ))}
                </div>
                {!last && (
                  <div style={{ textAlign: "center", marginTop: "-48px", marginBottom: "72px" }}>
                    <button className="coaches-filter-submit" onClick={() => fetchCoaches(page + 1, true)} disabled={loadingMore}>
                      {loadingMore ? "Yükleniyor…" : "Daha Fazla Göster"}
                    </button>
                  </div>
                )}
              </>
            )}
          </>
        ) : !authLoading ? (
          <div className="coaches-paywall">
            <div className="coaches-paywall__glow-a" />
            <div className="coaches-paywall__glow-b" />
            <div className="coaches-paywall__icon">
              <LockIcon />
            </div>
            <h2>Tüm Koçlarımızı Görmek İçin Kayıt Ol!</h2>
            <p>
              {user?.role === "COACH"
                ? "Koç hesabınızla koç listesine erişim bulunmuyor — bu ekran öğrenci hesapları içindir."
                : "Hedeflerine ulaşman için seni bekleyen mentorlerimizle tanışmak üzere ücretsiz bir öğrenci hesabı oluştur."}
            </p>
            {user?.role !== "COACH" && (
              <Link className="home-button home-button--primary" to="/register">
                Hemen Kayıt Ol
                <ArrowRightIcon />
              </Link>
            )}
            <p className="coaches-paywall__note">Ücretsiz hesap oluştur ve tüm koçlara anında ulaş.</p>
          </div>
        ) : null}
      </div>
    </div>
  );
};
