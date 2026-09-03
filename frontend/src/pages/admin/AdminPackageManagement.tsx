import { useEffect, useState } from "react";
import { adminApi } from "../../admin/adminApi";
import type { AdminPackage, AdminPackageCatalog, DiscountType } from "../../admin/adminTypes";

const money = new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" });
const localDateTime = (value?: string | null) => value ? new Date(value).toISOString().slice(0, 16) : "";

function CampaignEditor({ pkg, refresh }: { pkg: AdminPackage; refresh: () => Promise<void> }) {
  const [title, setTitle] = useState(pkg.campaign?.title ?? "");
  const [description, setDescription] = useState(pkg.campaign?.description ?? "");
  const [startsAt, setStartsAt] = useState(localDateTime(pkg.campaign?.startsAt));
  const [endsAt, setEndsAt] = useState(localDateTime(pkg.campaign?.endsAt));
  const [discountType, setDiscountType] = useState<DiscountType>(pkg.campaign?.discountType ?? "PERCENTAGE");
  const [discountValue, setDiscountValue] = useState(String(pkg.campaign?.discountValue ?? ""));
  const [error, setError] = useState("");

  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    const value = Number(discountValue);
    if (!title.trim() || !startsAt || !endsAt || !Number.isFinite(value) || value <= 0) {
      setError("Kampanya adı, tarih aralığı ve pozitif indirim değeri zorunludur."); return;
    }
    if (new Date(endsAt) <= new Date(startsAt)) { setError("Bitiş zamanı başlangıçtan sonra olmalıdır."); return; }
    if (discountType === "PERCENTAGE" && value > 100) { setError("Yüzde indirim 100'ü aşamaz."); return; }
    setError("");
    await adminApi.upsertCampaign(pkg.packageType, {
      enabled: pkg.campaign?.enabled ?? false, title: title.trim(), description: description.trim() || null,
      startsAt: new Date(startsAt).toISOString(), endsAt: new Date(endsAt).toISOString(), discountType, discountValue: value,
    });
    await refresh();
  };

  return <form className="admin-package-campaign" onSubmit={save}>
    <h4>Kampanya</h4>
    {pkg.campaign && <p className="admin-package-campaign__status">{pkg.campaign.currentlyActive ? "Şu anda aktif" : pkg.campaign.enabled ? "Zaman aralığı dışında" : "Devre dışı"}</p>}
    <label>Kampanya adı<input value={title} onChange={e => setTitle(e.target.value)} /></label>
    <label>Açıklama<input value={description} onChange={e => setDescription(e.target.value)} /></label>
    <div className="admin-package-form-grid">
      <label>Başlangıç<input type="datetime-local" value={startsAt} onChange={e => setStartsAt(e.target.value)} /></label>
      <label>Bitiş<input type="datetime-local" value={endsAt} onChange={e => setEndsAt(e.target.value)} /></label>
      <label>İndirim tipi<select value={discountType} onChange={e => setDiscountType(e.target.value as DiscountType)}><option value="PERCENTAGE">Yüzde</option><option value="FIXED_AMOUNT">Sabit tutar</option></select></label>
      <label>İndirim değeri<input type="number" min="0.01" step="0.01" value={discountValue} onChange={e => setDiscountValue(e.target.value)} /></label>
    </div>
    {error && <p className="admin-field-error" role="alert">{error}</p>}
    <div className="admin-package-actions"><button className="admin-button admin-button--secondary" type="submit">Kampanyayı Kaydet</button>{pkg.campaign && <button className="admin-button admin-button--secondary" type="button" onClick={async()=>{await adminApi.setCampaignEnabled(pkg.packageType,!pkg.campaign!.enabled);await refresh();}}>{pkg.campaign.enabled ? "Kampanyayı Kapat" : "Kampanyayı Aç"}</button>}</div>
  </form>;
}

function PackageEditor({ pkg, refresh }: { pkg: AdminPackage; refresh: () => Promise<void> }) {
  const [price, setPrice] = useState(pkg.basePrice == null ? "" : String(pkg.basePrice));
  const [error, setError] = useState("");
  const saveBase = async (event: React.FormEvent) => {
    event.preventDefault(); const value = Number(price);
    if (!Number.isFinite(value) || value <= 0) { setError("Fiyat sıfırdan büyük olmalıdır."); return; }
    setError(""); await adminApi.updatePackage(pkg.packageType, value, pkg.active); await refresh();
  };
  return <article className="admin-panel admin-package-card">
    <div className="admin-panel__heading"><div><h3>{pkg.name}</h3><p>{pkg.active ? "Aktif" : "Satışa kapalı"}</p></div><button className="admin-button admin-button--secondary" type="button" onClick={async()=>{await adminApi.setPackageActive(pkg.packageType,!pkg.active);await refresh();}}>{pkg.active ? "Paketi Kapat" : "Paketi Aç"}</button></div>
    <p className="admin-package-entitlement">Ayda {pkg.totalMeetingsPerMonth} görüşme · {pkg.evaluationMeetingsPerMonth} değerlendirme + {pkg.weeklyMeetingsPerMonth} haftalık görüşme</p>
    {pkg.packageType !== "UNTIL_EXAM" && <form className="admin-package-price" noValidate onSubmit={saveBase}><label>{pkg.name} temel fiyatı<input aria-label={`${pkg.name} temel fiyatı`} type="number" min="0.01" step="0.01" value={price} onChange={e=>setPrice(e.target.value)} /></label><button className="admin-button" type="submit">Fiyatı Kaydet</button></form>}
    <div className="admin-package-price-summary"><span>Normal fiyat: {pkg.basePrice == null ? "Tanımlı değil" : money.format(pkg.basePrice)}</span><strong>Etkin fiyat: {pkg.effectivePrice == null ? "Tanımlı değil" : money.format(pkg.effectivePrice)}</strong></div>
    {error && <p className="admin-field-error" role="alert">{error}</p>}
    <CampaignEditor pkg={pkg} refresh={refresh} />
  </article>;
}

export function AdminPackageManagement() {
  const [catalog, setCatalog] = useState<AdminPackageCatalog | null>(null);
  const [error, setError] = useState(false);
  const [examYear, setExamYear] = useState("");
  const [examDate, setExamDate] = useState("");
  const [examActive, setExamActive] = useState(false);
  const [examError, setExamError] = useState("");
  const [tierMonth, setTierMonth] = useState("");
  const [tierPrice, setTierPrice] = useState("");
  const load = async () => { try { const data=await adminApi.packages(); setCatalog(data); setExamYear(data.yksExamYear == null ? "" : String(data.yksExamYear)); setExamDate(data.yksExamDate ?? ""); setExamActive(data.yksExamActive); setError(false); } catch { setError(true); } };
  useEffect(()=>{void load();},[]);
  const untilExam = catalog?.packages.find(pkg=>pkg.packageType==="UNTIL_EXAM");
  return <section className="admin-package-management" aria-labelledby="admin-packages-title">
    <div className="admin-page__heading"><div><p className="admin-page__eyebrow">Ürün ve fiyatlandırma</p><h2 id="admin-packages-title">Paket Yönetimi</h2><p>Normal fiyatları, satış durumunu, sınava kalan ay tarifelerini ve paket bazlı kampanyaları yönetin.</p></div></div>
    {error && <div className="admin-error" role="alert">Paket yapılandırması alınamadı. <button onClick={()=>void load()}>Yeniden dene</button></div>}
    {catalog && <>
      <section className="admin-panel admin-exam-settings" aria-labelledby="admin-exam-settings-title"><div className="admin-panel__heading"><div><h3 id="admin-exam-settings-title">YKS Sınav Ayarları</h3><p>Aktif sınav, Sınava Kadar paketinin hangi fiyat satırını kullanacağını belirler.</p></div><span className={`admin-badge ${examActive ? "admin-badge--success" : ""}`}>{examActive ? "Aktif" : "Pasif"}</span></div><form className="admin-exam-config" noValidate onSubmit={async e=>{e.preventDefault();const year=Number(examYear);if(!Number.isInteger(year)||year<=0||!examDate||new Date(`${examDate}T00:00:00`).getFullYear()!==year){setExamError("YKS yılı ile sınav tarihinin yılı aynı olmalıdır.");return;}setExamError("");await adminApi.setExamSettings({examYear:year,examDate,active:examActive});await load();}}><label>Aktif YKS yılı<input aria-label="Aktif YKS yılı" type="number" min="1" value={examYear} onChange={e=>setExamYear(e.target.value)} /></label><label>Sınav tarihi<input aria-label="Sınav tarihi" type="date" value={examDate} onChange={e=>setExamDate(e.target.value)} /></label><label className="admin-exam-active"><input aria-label="Aktif YKS sınavı" type="checkbox" checked={examActive} onChange={e=>setExamActive(e.target.checked)} /> Aktif/current sınav</label><button className="admin-button" type="submit">Kaydet/Güncelle</button></form>{examError&&<p className="admin-field-error" role="alert">{examError}</p>}<p className="admin-package-campaign__status">{catalog.applicableMonthsRemaining ? `Şu an uygulanacak tarife: ${catalog.applicableMonthsRemaining} ay kala` : "Aktif ve gelecekte bir sınav olmadığından Sınava Kadar paketi kullanılamaz."}</p></section>
      <div className="admin-package-grid">{catalog.packages.map(pkg=><PackageEditor key={pkg.packageType} pkg={pkg} refresh={load} />)}</div>
      {untilExam && <section className="admin-panel admin-tier-panel"><div className="admin-panel__heading"><div><h3>Sınava Kadar Fiyatlandırma</h3><p>{catalog.yksExamYear && catalog.yksExamDate ? `${catalog.yksExamYear} · ${catalog.yksExamDate}` : "Önce YKS sınav ayarlarını tanımlayın."}</p></div></div><form className="admin-package-form-grid" onSubmit={async e=>{e.preventDefault();const month=Number(tierMonth),price=Number(tierPrice);if(month>0&&price>0){await adminApi.upsertPackageTier(month,price);setTierMonth("");setTierPrice("");await load();}}}><label>Kalan ay<input aria-label="Kalan ay" type="number" min="1" value={tierMonth} onChange={e=>setTierMonth(e.target.value)} /></label><label>Fiyat<input aria-label="Ay bazlı fiyat" type="number" min="0.01" step="0.01" value={tierPrice} onChange={e=>setTierPrice(e.target.value)} /></label><button className="admin-button" type="submit">Satır Ekle/Güncelle</button></form><div className="admin-tier-list">{untilExam.priceTiers.map(t=><div key={t.monthsRemaining}><span>{t.monthsRemaining} ay kala</span><strong>{money.format(t.price)}</strong><button type="button" onClick={async()=>{await adminApi.deletePackageTier(t.monthsRemaining);await load();}}>Sil</button></div>)}</div></section>}
    </>}
  </section>;
}
