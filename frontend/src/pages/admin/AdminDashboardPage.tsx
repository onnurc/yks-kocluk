import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { adminApi } from "../../admin/adminApi";
import type { AdminDashboardSummary, AdminSession } from "../../admin/adminTypes";
import "./admin.css";

const money = new Intl.NumberFormat("tr-TR", { style:"currency", currency:"TRY", maximumFractionDigits:0 });
const when = new Intl.DateTimeFormat("tr-TR", { day:"numeric", month:"short", hour:"2-digit", minute:"2-digit" });

export const AdminDashboardPage = () => {
  const [summary,setSummary]=useState<AdminDashboardSummary|null>(null); const [sessions,setSessions]=useState<AdminSession[]>([]);
  const [loading,setLoading]=useState(true); const [error,setError]=useState(false); const [version,setVersion]=useState(0);
  useEffect(()=>{let active=true;Promise.all([adminApi.summary(),adminApi.sessions({from:new Date().toISOString(),page:0,size:6})]).then(([s,e])=>{if(active){setSummary(s);setSessions(e.content??[]);}}).catch(()=>active&&setError(true)).finally(()=>active&&setLoading(false));return()=>{active=false};},[version]);
  const metrics=[['Toplam Öğrenci',summary?.totalStudentCount],['Toplam Koç',summary?.totalCoachCount],['Aktif Koç',summary?.activeCoachCount],['Aktif Abonelik',summary?.activeSubscriptionCount]] as const;
  return <main className="admin-page">
    <header className="admin-page__heading"><div><p className="admin-page__eyebrow">Operasyon merkezi</p><h1>Admin Paneli</h1><p>Platformun güncel durumunu ve ilgilenmeniz gereken işleri tek ekranda izleyin.</p></div></header>
    {error?<div className="admin-error" role="alert">Panel özeti alınamadı. <button className="admin-button admin-button--secondary" onClick={()=>{setLoading(true);setError(false);setVersion(v=>v+1)}}>Yeniden dene</button></div>:<>
      <section className="admin-metrics" aria-label="Platform özeti">{metrics.map(([label,value])=><article className="admin-metric" key={label}><span>{label}</span><strong>{loading?'…':value??0}</strong></article>)}</section>
      <section className="admin-metrics" aria-label="Aylık finans özeti"><article className="admin-metric admin-metric--navy"><span>Bu Ay Brüt Tahsilat</span><strong>{money.format(summary?.grossRevenueThisMonth??0)}</strong></article><article className="admin-metric"><span>Bu Ay İade</span><strong>{money.format(summary?.refundAmountThisMonth??0)}</strong></article><article className="admin-metric admin-metric--gold"><span>Bu Ay Net Tahsilat</span><strong>{money.format(summary?.netCollectedThisMonth??0)}</strong></article><article className="admin-metric"><span>Yaklaşan Seans</span><strong>{summary?.scheduledSessionCount??0}</strong></article></section>
      <div className="admin-grid"><section className="admin-panel"><div className="admin-panel__heading"><h2>Yaklaşan Görüşmeler</h2><Link to="/admin/sessions">Tümünü gör</Link></div>{sessions.length===0?<div className="admin-empty">Yaklaşan seans veya deneme görüşmesi yok.</div>:<div className="admin-list">{sessions.map(s=><article className="admin-list-item" key={`${s.type}-${s.id}`}><div><h3>{s.studentName} · {s.coachName}</h3><p>{s.type==='TRIAL'?'Deneme görüşmesi':'Ücretli seans'} · {when.format(new Date(s.startsAt))}</p></div><span className="admin-badge admin-badge--navy">{s.status}</span></article>)}</div>}</section>
      <aside className="admin-panel"><div className="admin-panel__heading"><h2>İlgilenmeniz Gerekenler</h2></div><div className="admin-list"><Link className="admin-link-card" to="/admin/coach-applications"><strong>{summary?.pendingCoachApplicationCount??0}</strong><span>bekleyen koç başvurusu</span></Link><Link className="admin-link-card" to="/admin/reports"><strong>{summary?.openReportCount??0}</strong><span>açık veya incelenen rapor</span></Link><Link className="admin-link-card" to="/admin/finance"><strong>{summary?.salesThisMonthCount??0}</strong><span>bu ay başarılı tahsilat</span></Link></div></aside></div>
    </>}
  </main>;
};
