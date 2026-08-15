import React from "react";
import { Link } from "react-router-dom";
import type { CoachSummaryResponse, Track } from "./coachDiscoveryTypes";
import { TRACK_LABELS } from "./coachDiscoveryTypes";

export const StarIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="currentColor">
    <path d="m12 2 2.9 6.26 6.9.6-5.2 4.6 1.6 6.79L12 16.9l-6.2 3.35 1.6-6.79-5.2-4.6 6.9-.6L12 2Z" />
  </svg>
);

export const ArrowRightIcon: React.FC = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
    <path d="M5 12h14M13 6l6 6-6 6" />
  </svg>
);

export function coachInitials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? "";
  const last = parts.length > 1 ? parts[parts.length - 1][0] : "";
  return (first + last).toUpperCase();
}

export function CoachCard({ coach }: { coach: CoachSummaryResponse }) {
  return (
    <article className="coach-card">
      <span className={`coach-card__badge ${coach.acceptingNewStudents ? "coach-card__badge--open" : "coach-card__badge--full"}`}>
        {coach.acceptingNewStudents ? "Öğrenci Alıyor" : "Kontenjan Dolu"}
      </span>

      <div className="coach-card__avatar">
        {coach.profileImageUrl ? <img src={coach.profileImageUrl} alt={coach.fullName} /> : coachInitials(coach.fullName)}
      </div>

      <h3>{coach.fullName}</h3>
      <p className="coach-card__meta">
        {coach.universityName}
        {coach.headline ? ` · ${coach.headline}` : ""}
      </p>

      {coach.tracks.length > 0 && (
        <div className="coach-card__tracks">
          {coach.tracks.map((track) => (
            <span className="coach-card__track" key={track}>
              {TRACK_LABELS[track as Track] ?? track}
            </span>
          ))}
        </div>
      )}

      <div className="coach-card__divider" />

      <div className="coach-card__rating">
        <StarIcon />
        {coach.rating != null ? `${coach.rating.toFixed(1)} · ${coach.totalSessions} seans` : "Yeni koç"}
      </div>

      <Link className="coach-card__cta" to={`/coaches/${coach.id}`}>
        Profili İncele
        <ArrowRightIcon />
      </Link>
    </article>
  );
}
