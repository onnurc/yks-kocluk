import { useEffect, useState } from "react";
import { ApiError } from "../api/ApiError";
import { refundRequestApi, type RefundEligibilityResponse } from "./refundRequestApi";
import { notifySubscriptionStateChanged } from "../studentDashboard/studentDashboardApi";

interface Props { subscriptionId:number; onSuccess:()=>void; }
const money=(amount:number,currency:string)=>new Intl.NumberFormat("tr-TR",{style:"currency",currency}).format(amount);
const date=(value:string)=>new Date(value).toLocaleDateString("tr-TR",{year:"numeric",month:"long",day:"numeric"});

export const RefundRequestAction=({subscriptionId,onSuccess}:Props)=>{
  const [eligibility,setEligibility]=useState<RefundEligibilityResponse|null>(null); const [loading,setLoading]=useState(true);
  const [open,setOpen]=useState(false); const [busy,setBusy]=useState(false); const [error,setError]=useState<string|null>(null);
  const [completed,setCompleted]=useState(false);
  useEffect(()=>{let active=true;refundRequestApi.eligibility(subscriptionId).then((value)=>active&&setEligibility(value)).catch(()=>active&&setError("İade uygunluğu şu anda alınamadı.")).finally(()=>active&&setLoading(false));return()=>{active=false};},[subscriptionId]);
  const submit=async()=>{if(busy)return;setBusy(true);setError(null);try{await refundRequestApi.create(subscriptionId);setOpen(false);setCompleted(true);setEligibility((current)=>current?{...current,eligible:false,status:"NO_REFUNDABLE_BALANCE",activeRequestStatus:"REFUNDED",refundableAmount:0,explanation:`İade işleminiz tamamlandı. Erişiminiz ${date(current.accessEndsAt)} tarihine kadar devam eder.`}:current);notifySubscriptionStateChanged();onSuccess();}catch(caught){setError(caught instanceof ApiError?caught.detail:"İade tamamlanamadı.");}finally{setBusy(false)}};
  if (!loading && eligibility?.packageType === "ONE_MONTH") return <div className="subscription-management__refund"><div><strong>İade kapsamı dışında</strong><p>{eligibility.explanation}</p></div></div>;
  return <div className="subscription-management__refund"><div><strong>{completed?"İade tamamlandı":"İptal ve iade"}</strong><p role={completed||eligibility?.activeRequestStatus ? "status" : undefined}>{loading?"İade uygunluğu kontrol ediliyor…":eligibility?.explanation??error}</p>{eligibility?.eligible&&<small>{eligibility.usedMonthCount} hizmet ayı kullanılmış sayılır · Erişim sonu: {date(eligibility.accessEndsAt)} · İade: {money(eligibility.refundableAmount,eligibility.currency)}</small>}{eligibility?.deadline&&!completed&&<small>Son iade tarihi: {date(eligibility.deadline)}</small>}</div>{eligibility?.eligible&&<button type="button" disabled={loading||completed} onClick={()=>setOpen(true)}>{completed?"İade Tamamlandı":"İptal ve İade Talebi"}</button>}
    {open&&eligibility&&<div className="subscription-modal__backdrop"><section className="subscription-modal" role="dialog" aria-modal="true" aria-labelledby="refund-request-title"><p className="subscription-modal__eyebrow">Abonelik işlemi</p><h3 id="refund-request-title">İptal ve iadeyi doğrula</h3><p>Kullanılmış sayılan hizmet ayı: <strong>{eligibility.usedMonthCount}</strong></p><p>Erişim bitişi: <strong>{date(eligibility.accessEndsAt)}</strong></p><p>İade tutarı: <strong>{money(eligibility.refundableAmount,eligibility.currency)}</strong></p><p>İçinde bulunduğunuz hizmet ayı tam kullanılmış sayılır. Erişiminiz bu hizmet ayının sonuna kadar devam eder ve sonraki hizmet ayına uzamaz.</p>{error&&<p className="subscription-modal__error" role="alert">{error}</p>}<div className="subscription-modal__actions"><button className="subscription-modal__cancel" type="button" disabled={busy} onClick={()=>setOpen(false)}>Vazgeç</button><button className="subscription-modal__confirm" type="button" disabled={busy} onClick={()=>void submit()}>{busy?"İşleniyor…":"İptal ve İadeyi Onayla"}</button></div></section></div>}
  </div>;
};
