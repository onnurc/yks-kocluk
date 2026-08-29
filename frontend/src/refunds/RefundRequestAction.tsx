import { useEffect, useState } from "react";
import { ApiError } from "../api/ApiError";
import { refundRequestApi, type RefundEligibilityResponse } from "./refundRequestApi";

interface Props { subscriptionId:number; onSuccess:()=>void; }
const money=(amount:number,currency:string)=>new Intl.NumberFormat("tr-TR",{style:"currency",currency}).format(amount);

export const RefundRequestAction=({subscriptionId,onSuccess}:Props)=>{
  const [eligibility,setEligibility]=useState<RefundEligibilityResponse|null>(null); const [loading,setLoading]=useState(true);
  const [open,setOpen]=useState(false); const [busy,setBusy]=useState(false); const [error,setError]=useState<string|null>(null);
  useEffect(()=>{let active=true;refundRequestApi.eligibility(subscriptionId).then((value)=>active&&setEligibility(value)).catch(()=>active&&setError("İade uygunluğu şu anda alınamadı.")).finally(()=>active&&setLoading(false));return()=>{active=false};},[subscriptionId]);
  const submit=async()=>{if(busy)return;setBusy(true);setError(null);try{await refundRequestApi.create(subscriptionId);setOpen(false);setEligibility((current)=>current?{...current,eligible:false,status:"ACTIVE_REQUEST_EXISTS",activeRequestStatus:"PENDING",explanation:"İade talebiniz incelemeye alındı."}:current);onSuccess();}catch(caught){setError(caught instanceof ApiError?caught.detail:"İade talebi oluşturulamadı.");}finally{setBusy(false)}};
  return <div className="subscription-management__refund"><div><strong>İade talebi</strong><p role={eligibility?.activeRequestStatus ? "status" : undefined}>{loading?"İade uygunluğu kontrol ediliyor…":eligibility?.explanation??error}</p>{eligibility?.deadline&&<small>Son talep tarihi: {new Date(eligibility.deadline).toLocaleDateString("tr-TR")}</small>}</div><button type="button" disabled={loading||!eligibility?.eligible} onClick={()=>setOpen(true)}>İade Talep Et</button>
    {open&&eligibility&&<div className="subscription-modal__backdrop"><section className="subscription-modal" role="dialog" aria-modal="true" aria-labelledby="refund-request-title"><p className="subscription-modal__eyebrow">Abonelik işlemi</p><h3 id="refund-request-title">İade talebini doğrula</h3><p>İade edilebilir tutar: <strong>{money(eligibility.refundableAmount,eligibility.currency)}</strong></p><p>{eligibility.explanation}</p>{error&&<p className="subscription-modal__error" role="alert">{error}</p>}<div className="subscription-modal__actions"><button className="subscription-modal__cancel" type="button" disabled={busy} onClick={()=>setOpen(false)}>Vazgeç</button><button className="subscription-modal__confirm" type="button" disabled={busy} onClick={()=>void submit()}>{busy?"Gönderiliyor…":"Talebi Gönder"}</button></div></section></div>}
  </div>;
};
