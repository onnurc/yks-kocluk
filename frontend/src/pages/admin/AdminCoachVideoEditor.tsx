import { useState } from "react";
import type { FormEvent } from "react";
import { adminApi } from "../../admin/adminApi";
import type { AdminCoach } from "../../admin/adminTypes";
import { ApiError } from "../../api/ApiError";

export function AdminCoachVideoEditor({ coach, onUpdated }: {
  coach: AdminCoach;
  onUpdated: (coach: AdminCoach) => void;
}) {
  const [reference, setReference] = useState(coach.introYoutubeVideoId ?? "");
  const [busy, setBusy] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [feedback, setFeedback] = useState<{ type: "success" | "error"; message: string } | null>(null);

  const refreshCoach = async (introYoutubeVideoId: string | null) => {
    const optimistic = { ...coach, introYoutubeVideoId };
    onUpdated(optimistic);
    try {
      onUpdated(await adminApi.coach(coach.coachProfileId));
    } catch {
      // The write succeeded; retain the endpoint response if the detail refresh is unavailable.
    }
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
    const value = reference.trim();
    if (!value || busy || value === coach.introYoutubeVideoId) return;

    setBusy(true);
    setFeedback(null);
    try {
      const response = await adminApi.setCoachYoutubeIntro(coach.coachProfileId, value);
      setReference(response.videoId);
      await refreshCoach(response.videoId);
      setFeedback({ type: "success", message: "Tanıtım videosu kaydedildi." });
    } catch (error) {
      setFeedback({
        type: "error",
        message: error instanceof ApiError && error.code === "COACH_YOUTUBE_URL_INVALID"
          ? "Geçerli bir YouTube Shorts bağlantısı veya video kimliği girin."
          : "Tanıtım videosu kaydedilemedi. Lütfen yeniden deneyin.",
      });
    } finally {
      setBusy(false);
    }
  };

  const remove = async () => {
    if (!coach.introYoutubeVideoId || busy) return;
    setBusy(true);
    setFeedback(null);
    try {
      await adminApi.removeCoachYoutubeIntro(coach.coachProfileId);
      setReference("");
      setConfirmRemove(false);
      await refreshCoach(null);
      setFeedback({ type: "success", message: "Tanıtım videosu kaldırıldı." });
    } catch {
      setFeedback({ type: "error", message: "Tanıtım videosu kaldırılamadı. Lütfen yeniden deneyin." });
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="admin-video-editor" aria-labelledby="admin-coach-video-title">
      <h3 id="admin-coach-video-title">Koç Tanıtım Videosu</h3>
      {coach.introYoutubeVideoId ? (
        <p className="admin-video-editor__current">
          Atanmış video: <code>{coach.introYoutubeVideoId}</code>{" "}
          <a href={`https://www.youtube.com/shorts/${coach.introYoutubeVideoId}`} target="_blank" rel="noreferrer">
            YouTube’da görüntüle
          </a>
        </p>
      ) : (
        <p className="admin-video-editor__current">Bu koça henüz bir tanıtım videosu atanmamış.</p>
      )}

      <form className="admin-form" onSubmit={(event) => void save(event)}>
        <label htmlFor="admin-coach-youtube-reference">
          YouTube Shorts bağlantısı veya video kimliği
          <input
            id="admin-coach-youtube-reference"
            value={reference}
            maxLength={300}
            placeholder="https://youtube.com/shorts/…"
            onChange={(event) => {
              setReference(event.target.value);
              setFeedback(null);
            }}
            disabled={busy}
            required
          />
        </label>
        <div className="admin-actions">
          <button className="admin-button" type="submit" disabled={busy || !reference.trim() || reference.trim() === coach.introYoutubeVideoId}>
            {busy ? "İşleniyor…" : coach.introYoutubeVideoId ? "Videoyu Güncelle" : "Videoyu Kaydet"}
          </button>
          {coach.introYoutubeVideoId && !confirmRemove && (
            <button className="admin-button admin-button--danger" type="button" disabled={busy} onClick={() => setConfirmRemove(true)}>
              Videoyu Kaldır
            </button>
          )}
        </div>
      </form>

      {confirmRemove && (
        <div className="admin-video-editor__confirmation" role="group" aria-label="Video kaldırma onayı">
          <p>Bu koçun tanıtım videosu atamasını kaldırmak istediğinize emin misiniz?</p>
          <div className="admin-actions">
            <button className="admin-button admin-button--secondary" type="button" disabled={busy} onClick={() => setConfirmRemove(false)}>Vazgeç</button>
            <button className="admin-button admin-button--danger" type="button" disabled={busy} onClick={() => void remove()}>
              {busy ? "Kaldırılıyor…" : "Kaldırmayı Onayla"}
            </button>
          </div>
        </div>
      )}

      {feedback && (
        <p className={`admin-feedback${feedback.type === "error" ? " is-error" : ""}`} role={feedback.type === "error" ? "alert" : "status"}>
          {feedback.message}
        </p>
      )}
    </section>
  );
}
