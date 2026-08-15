import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthProvider";
import { readinessPathForUser } from "../auth/authNavigation";
import { coachDiscoveryApi, type CoachSearchFilters } from "../coaches/coachDiscoveryApi";
import type { CoachSummaryResponse } from "../coaches/coachDiscoveryTypes";
import "./coaches-page.css";

const PAGE_SIZE = 9;

const tracks = [
  { value: "NUMERICAL", label: "Sayısal", icon: "△" },
  { value: "EQUAL_WEIGHT", label: "Eşit Ağırlık", icon: "⚖" },
  { value: "VERBAL", label: "Sözel", icon: "▤" },
  { value: "LANGUAGE", label: "Dil", icon: "◎" },
] as const;

const trackLabels = Object.fromEntries(tracks.map((track) => [track.value, track.label]));

function initials(name: string) {
  return name.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toLocaleUpperCase("tr-TR");
}

function CoachCard({ coach }: { coach: CoachSummaryResponse }) {
  const visibleTracks = coach.tracks?.map((track) => trackLabels[track] ?? track) ?? [];

  return (
    <article className="coaches-card">
      <div className="coaches-card__badges">
        {coach.acceptingNewStudents && <span className="coaches-card__badge coaches-card__badge--gold">Yeni öğrenci kabul ediyor</span>}
        {visibleTracks.map((track) => <span className="coaches-card__badge" key={track}>{track}</span>)}
      </div>
      <div className="coaches-card__portrait">
        {coach.profileImageUrl ? (
          <img src={coach.profileImageUrl} alt={`${coach.fullName} profil fotoğrafı`} />
        ) : (
          <span aria-label={`${coach.fullName} için profil fotoğrafı bulunmuyor`}>{initials(coach.fullName)}</span>
        )}
      </div>
      <h2>{coach.fullName}</h2>
      <p className="coaches-card__school">{[coach.universityName, coach.headline].filter(Boolean).join(" · ")}</p>
      <div className="coaches-card__divider" />
      <div className="coaches-card__facts">
        {coach.rating != null && <span aria-label={`Puan ${coach.rating.toFixed(1)}`}>★ {coach.rating.toFixed(1)}</span>}
        {coach.totalSessions > 0 && <span>{coach.totalSessions} tamamlanan seans</span>}
        {coach.rating == null && coach.totalSessions === 0 && <span>Profil bilgilerini inceleyin</span>}
      </div>
      <Link to={`/coaches/${coach.id}`} className="coaches-card__link">
        Profili İncele <span aria-hidden="true">→</span>
      </Link>
    </article>
  );
}

function CoachSkeleton() {
  return (
    <div className="coaches-card coaches-card--loading" aria-hidden="true">
      <span /><span /><span /><span />
    </div>
  );
}

export function CoachesPage() {
  const { isAuthenticated, user } = useAuth();
  const [coaches, setCoaches] = useState<CoachSummaryResponse[]>([]);
  const [searchDraft, setSearchDraft] = useState("");
  const [trackDraft, setTrackDraft] = useState("");
  const [sortDraft, setSortDraft] = useState<"newest" | "oldest">("newest");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    document.title = "Koçlarımız | Uniform Akademi";
  }, []);

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      setDebouncedSearch(searchDraft.trim());
      setPage(0);
    }, 300);
    return () => window.clearTimeout(timeout);
  }, [searchDraft]);

  const filters = useMemo<CoachSearchFilters>(() => ({
    ...(debouncedSearch ? { q: debouncedSearch } : {}),
    ...(trackDraft ? { track: trackDraft } : {}),
    sort: sortDraft,
  }), [debouncedSearch, sortDraft, trackDraft]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(false);
    coachDiscoveryApi.listCoaches(page, PAGE_SIZE, filters)
      .then((response) => {
        if (cancelled) return;
        setCoaches(response.content ?? []);
        setTotalPages(response.totalPages ?? 0);
        setTotalElements(response.totalElements ?? 0);
      })
      .catch(() => {
        if (!cancelled) setError(true);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [filters, page]);

  const resultText = useMemo(() => {
    if (loading || error) return "";
    return totalElements === 1 ? "1 koç bulundu" : `${totalElements} koç bulundu`;
  }, [error, loading, totalElements]);

  const chooseTrack = (track: string) => {
    const nextTrack = trackDraft === track ? "" : track;
    setTrackDraft(nextTrack);
    setPage(0);
  };

  const selectTrack = (track: string) => {
    setTrackDraft(track);
    setPage(0);
  };

  const selectSort = (sort: "newest" | "oldest") => {
    setSortDraft(sort);
    setPage(0);
  };

  const retry = () => {
    setLoading(true);
    setError(false);
    coachDiscoveryApi.listCoaches(page, PAGE_SIZE, filters)
      .then((response) => {
        setCoaches(response.content ?? []);
        setTotalPages(response.totalPages ?? 0);
        setTotalElements(response.totalElements ?? 0);
      })
      .catch(() => setError(true))
      .finally(() => setLoading(false));
  };

  const changePage = (nextPage: number) => {
    setLoading(true);
    setError(false);
    setPage(nextPage);
  };

  return (
    <div className="coaches-page">
      <section className="coaches-hero" aria-labelledby="coaches-title">
        <p className="coaches-eyebrow"><span aria-hidden="true">⌕</span> Geleceğini Tasarla</p>
        <h1 id="coaches-title">Hayalindeki Üniversiteye Giden Yolda,<br /><em>En Doğru Rehberi Bul.</em></h1>
        <p>Türkiye'nin seçkin üniversitelerinde okuyan mentörlerimizle tanışın. Size en uygun koçu bulmak için filtreleri kullanın.</p>
      </section>

      <div className="coaches-content">
        <div className="coaches-filters" aria-label="Koç arama filtreleri">
          <div className="coaches-filters__main">
            <label className="coaches-search">
              <span aria-hidden="true">⌕</span>
              <span className="coaches-sr-only">Koç ara</span>
              <input value={searchDraft} onChange={(event) => setSearchDraft(event.target.value)} placeholder="İsim veya uzmanlık ara..." />
            </label>
            <label className="coaches-select">
              <span>Alan</span>
              <select value={trackDraft} onChange={(event) => selectTrack(event.target.value)}>
                <option value="">Tümü</option>
                {tracks.map((track) => <option key={track.value} value={track.value}>{track.label}</option>)}
              </select>
            </label>
            <label className="coaches-select">
              <span>Sıralama</span>
              <select value={sortDraft} onChange={(event) => selectSort(event.target.value as "newest" | "oldest")}>
                <option value="newest">En yeni</option>
                <option value="oldest">En eski</option>
              </select>
            </label>
          </div>
          <div className="coaches-filter-chips" aria-label="Alan hızlı filtreleri">
            {tracks.map((track) => (
              <button key={track.value} type="button" aria-pressed={trackDraft === track.value} onClick={() => chooseTrack(track.value)}>
                <span aria-hidden="true">{track.icon}</span> {track.label}
              </button>
            ))}
          </div>
        </div>

        <div className="coaches-results-heading" aria-live="polite">{resultText}</div>

        {loading && (
          <div className="coaches-grid" aria-label="Koçlar yükleniyor" aria-busy="true">
            {Array.from({ length: 3 }, (_, index) => <CoachSkeleton key={index} />)}
          </div>
        )}

        {!loading && error && (
          <div className="coaches-state" role="alert">
            <h2>Koçlar şu anda görüntülenemiyor.</h2>
            <p>Lütfen bağlantınızı kontrol edip yeniden deneyin.</p>
            <button type="button" onClick={retry}>Yeniden Dene</button>
          </div>
        )}

        {!loading && !error && coaches.length === 0 && (
          <div className="coaches-state" role="status">
            <h2>Bu filtrelere uygun koç bulunamadı.</h2>
            <p>Aramanızı değiştirerek tekrar deneyebilirsiniz.</p>
          </div>
        )}

        {!loading && !error && coaches.length > 0 && (
          <>
            <div className="coaches-grid">{coaches.map((coach) => <CoachCard key={coach.id} coach={coach} />)}</div>
            {totalPages > 1 && (
              <nav className="coaches-pagination" aria-label="Koç sayfaları">
                <button type="button" disabled={page === 0} onClick={() => changePage(page - 1)}>← Önceki</button>
                <span>{page + 1} / {totalPages}</span>
                <button type="button" disabled={page + 1 >= totalPages} onClick={() => changePage(page + 1)}>Sonraki →</button>
              </nav>
            )}
          </>
        )}

        <section className="coaches-cta" aria-labelledby="coaches-cta-title">
          <span className="coaches-cta__icon" aria-hidden="true">♙</span>
          <h2 id="coaches-cta-title">Koçunla Yola Çıkmaya Hazır mısın?</h2>
          <p>Ücretsiz hesabını oluştur, sana uygun koçun profilini incelemeye devam et.</p>
          <Link to={isAuthenticated && user ? readinessPathForUser(user) : "/register"}>
            {isAuthenticated && user ? "Panele Git" : "Hemen Kayıt Ol"} <span aria-hidden="true">→</span>
          </Link>
        </section>
      </div>
    </div>
  );
}
