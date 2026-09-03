import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { publicPackagesApi, type PublicPackage } from "./publicPackagesApi";
import "./coaching-page.css";

const trustItems = ["Derece Yapan Mentörler", "Veriye Dayalı Takip", "Kişiselleştirilmiş Plan"] as const;

const faqItems = [
  {
    question: "Koçluk görüşmeleri nasıl yapılıyor?",
    answer:
      "Görüşmeler çevrim içi olarak gerçekleştirilir. Haftalık görüşmelerde önceki haftanın analizi yapılır ve yeni haftanın programı oluşturulur.",
  },
  {
    question: "Mentörleriniz kimlerden oluşuyor?",
    answer:
      "Mentörlerimiz güçlü akademik geçmişe sahip, öğrenci rehberliği için değerlendirme ve eğitim süreçlerinden geçen üniversite öğrencileri ile mezunlardan oluşur.",
  },
  {
    question: "Paket değiştirmek mümkün mü?",
    answer:
      "Paket değişikliği ve geçiş koşulları mevcut abonelik durumuna göre değerlendirilir. Güncel seçenekler hesabınız üzerinden paylaşılır.",
  },
] as const;

const currencyFormatter = new Intl.NumberFormat("tr-TR", {
  style: "currency",
  currency: "TRY",
  maximumFractionDigits: 0,
});

function PackageSkeleton() {
  return (
    <article className="coaching-package coaching-package--loading" aria-hidden="true">
      <span /><span /><span /><span /><span />
    </article>
  );
}

function PackageCard({ coachingPackage, featured }: { coachingPackage: PublicPackage; featured: boolean }) {
  return (
    <article className={featured ? "coaching-package coaching-package--featured" : "coaching-package"}>
      {featured && <span className="coaching-package__badge">En Popüler</span>}
      <h3>{coachingPackage.name}</h3>
      <p className="coaching-package__price">{coachingPackage.effectivePrice == null ? "Şu anda kullanılamıyor" : currencyFormatter.format(coachingPackage.effectivePrice)}{coachingPackage.effectivePrice != null && <small> / paket</small>}</p>
      <p className="coaching-package__description">
        {coachingPackage.packageType === "UNTIL_EXAM"
          ? `Sınava kalan ${coachingPackage.untilExamMonthsRemaining} aylık mentörlük planı.`
          : `${coachingPackage.durationMonths} aylık mentörlük planı.`}
      </p>
      <ul>
        <li>Ayda {coachingPackage.totalMeetingsPerMonth} görüşme</li>
        <li>{coachingPackage.evaluationMeetingsPerMonth} değerlendirme + {coachingPackage.weeklyMeetingsPerMonth} haftalık görüşme</li>
        <li>Kişiselleştirilmiş çalışma planı</li>
        <li>{coachingPackage.packageType === "ONE_MONTH" ? "İade kapsamı dışında" : coachingPackage.packageType === "THREE_MONTHS" ? "İptalde mevcut hizmet ayı sonuna kadar erişim ve kalan tutar için iade hesabı" : "İptal ve iade koşulları mevcut hizmet dönemi hesabına göre belirlenir"}</li>
      </ul>
      {coachingPackage.purchasable ? <Link to="/register">{featured ? "Hemen Başla" : "Planı Seç"}</Link> : <span className="coaching-package__unavailable" aria-disabled="true">Satışa Kapalı</span>}
    </article>
  );
}

export function CoachingPage() {
  const [packages, setPackages] = useState<PublicPackage[]>([]);
  const [status, setStatus] = useState<"loading" | "success" | "error">("loading");

  const loadPackages = useCallback(async () => {
    try {
      const response = await publicPackagesApi.list();
      setPackages(response);
      setStatus("success");
    } catch {
      setPackages([]);
      setStatus("error");
    }
  }, []);

  const retryPackages = useCallback(() => {
    setStatus("loading");
    void loadPackages();
  }, [loadPackages]);

  useEffect(() => {
    let cancelled = false;

    publicPackagesApi.list().then((response) => {
      if (!cancelled) {
        setPackages(response);
        setStatus("success");
      }
    }).catch(() => {
      if (!cancelled) {
        setPackages([]);
        setStatus("error");
      }
    });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const previousTitle = document.title;
    document.title = "Koçluk | Uniform Akademi";
    return () => {
      document.title = previousTitle;
    };
  }, []);

  const featuredPackageId = useMemo(
    () => packages.find((item) => item.packageType === "THREE_MONTHS")?.id ?? packages[1]?.id,
    [packages],
  );

  return (
    <div className="coaching-page">
      <section className="coaching-hero" aria-labelledby="coaching-title">
        <div className="coaching-hero__glow" aria-hidden="true" />
        <div className="coaching-hero__inner">
          <p className="coaching-eyebrow">Uniform Akademik Koçluk</p>
          <h1 id="coaching-title">Akademik Başarıya<br className="coaching-desktop-break" /> Giden Yolda, <span>Seninle Birlikteyiz.</span></h1>
          <p className="coaching-hero__lead">Hayallerindeki üniversiteye ulaşman için disiplinli, veriye dayalı ve kişiselleştirilmiş koçluk sistemi. Hedeflerine özel stratejilerle başarıyı şansa bırakmıyoruz.</p>
          <div className="coaching-hero__actions">
            <Link to="/register" className="coaching-button coaching-button--gold">Ücretsiz Görüşme Başlat</Link>
            <a href="#kocluk-paketleri" className="coaching-button coaching-button--light">Paketleri İncele</a>
          </div>
          <ul className="coaching-trust" aria-label="Koçluk sistemi avantajları">
            {trustItems.map((item, index) => <li key={item}><span aria-hidden="true">{index === 0 ? "✓" : index === 1 ? "↗" : "◎"}</span>{item}</li>)}
          </ul>
        </div>
      </section>

      <section className="coaching-media" aria-labelledby="coaching-media-title">
        <div className="coaching-container">
          <div className="coaching-media__frame">
            <img src="/images/coaching/mentoring-session.jpg" alt="Aydınlık bir çalışma alanında akademik planı birlikte inceleyen mentör ve öğrenci" fetchPriority="high" />
            <button type="button" disabled aria-label="Tanıtım videosu henüz kullanıma hazır değil"><span aria-hidden="true">▶</span></button>
          </div>
          <h2 id="coaching-media-title">Koçluk Sistemimizi 2 Dakikada Tanıyın</h2>
          <p>Sürecin nasıl işlediğini, mentörünle nasıl eşleştiğini ve haftalık takiplerin nasıl yapıldığını keşfet.</p>
        </div>
      </section>

      <section className="coaching-packages" id="kocluk-paketleri" aria-labelledby="coaching-packages-title">
        <div className="coaching-container">
          <div className="coaching-section-heading">
            <p>Yatırımınız</p>
            <h2 id="coaching-packages-title">Koçluk Paketleri</h2>
            <span>İhtiyacına ve hedefine en uygun planı seçerek başarı yolculuğuna hemen başla.</span>
          </div>

          {status === "loading" && (
            <div className="coaching-package-grid" aria-label="Koçluk paketleri yükleniyor" aria-busy="true">
              <PackageSkeleton /><PackageSkeleton /><PackageSkeleton />
            </div>
          )}

          {status === "error" && (
            <div className="coaching-packages__state" role="status">
              <h3>Paketler şu anda görüntülenemiyor.</h3>
              <p>Lütfen kısa süre sonra yeniden deneyin.</p>
              <button type="button" onClick={retryPackages}>Yeniden Dene</button>
            </div>
          )}

          {status === "success" && packages.length === 0 && (
            <div className="coaching-packages__state" role="status">
              <h3>Aktif koçluk paketi bulunmuyor.</h3>
              <p>Yeni paketler hazır olduğunda burada yayınlanacak.</p>
            </div>
          )}

          {status === "success" && packages.length > 0 && (
            <div className="coaching-package-grid">
              {packages.map((item) => <PackageCard key={item.id} coachingPackage={item} featured={item.id === featuredPackageId} />)}
            </div>
          )}
        </div>
      </section>

      <section className="coaching-faq" aria-labelledby="coaching-faq-title">
        <div className="coaching-faq__inner">
          <div className="coaching-section-heading">
            <p>Bilgi Merkezi</p>
            <h2 id="coaching-faq-title">Sıkça Sorulan Sorular</h2>
          </div>
          <div className="coaching-faq__list">
            {faqItems.map((item) => (
              <details key={item.question}>
                <summary><span>{item.question}</span><span aria-hidden="true">⌄</span></summary>
                <p>{item.answer}</p>
              </details>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
