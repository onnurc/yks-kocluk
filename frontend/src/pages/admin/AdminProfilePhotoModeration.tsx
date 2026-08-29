import { useState } from "react";
import { ApiError } from "../../api/ApiError";
import { adminApi } from "../../admin/adminApi";

interface Props { name:string; imageUrl?:string|null; assetId?:number|null; onRemoved:()=>void; }

export const AdminProfilePhotoModeration = ({ name, imageUrl, assetId, onRemoved }: Props) => {
  const [confirming, setConfirming] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string|null>(null);

  const remove = async () => {
    if (!assetId || !reason.trim() || busy) return;
    setBusy(true); setError(null);
    try { await adminApi.removeProfileImage(assetId, reason.trim()); setConfirming(false); setReason(""); onRemoved(); }
    catch (caught) { setError(caught instanceof ApiError ? caught.detail : "Fotoğraf kaldırılamadı."); }
    finally { setBusy(false); }
  };

  return <section className="admin-profile-photo" aria-label="Profil fotoğrafı moderasyonu">
    <div className="admin-profile-photo__preview">{imageUrl ? <img src={imageUrl} alt={`${name} profil fotoğrafı`} /> : <span>Profil fotoğrafı yok</span>}</div>
    {assetId && !confirming && <button className="admin-button admin-button--danger" type="button" onClick={() => setConfirming(true)}>Fotoğrafı Kaldır</button>}
    {confirming && <div className="admin-form"><label>Moderasyon gerekçesi<textarea rows={3} maxLength={1000} value={reason} onChange={(event)=>setReason(event.target.value)} /></label>{error&&<p className="admin-feedback is-error" role="alert">{error}</p>}<div className="admin-actions"><button className="admin-button admin-button--secondary" type="button" disabled={busy} onClick={()=>{setConfirming(false);setError(null)}}>Vazgeç</button><button className="admin-button admin-button--danger" type="button" disabled={busy||!reason.trim()} onClick={()=>void remove()}>{busy?"Kaldırılıyor…":"Kaldırmayı Onayla"}</button></div></div>}
  </section>;
};
