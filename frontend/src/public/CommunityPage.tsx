import { useEffect } from "react";
import { Link } from "react-router-dom";
import "./community-page.css";

type CommunityIconName =
  | "campus"
  | "coffee"
  | "profession"
  | "spark"
  | "calendar"
  | "people"
  | "route"
  | "book"
  | "building"
  | "crown"
  | "location";

const iconPaths: Record<CommunityIconName, string[]> = {
  campus: ["M3 10 12 4l9 6-9 6-9-6Z", "M5 20h14M7 12v5m5-5v5m5-5v5"],
  coffee: ["M5 8h11v5a5 5 0 0 1-5 5H9a4 4 0 0 1-4-4V8Z", "M16 10h1a3 3 0 0 1 0 6h-2M7 4c0 1 1 1 1 2m4-2c0 1 1 1 1 2"],
  profession: ["M4 20h16M6 20V7l6-3 6 3v13", "M9 9h1m4 0h1m-6 4h1m4 0h1m-6 4h6"],
  spark: ["m12 3 1.7 4.3L18 9l-4.3 1.7L12 15l-1.7-4.3L6 9l4.3-1.7L12 3Z", "m18.5 15 .8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8.8-2.2Z"],
  calendar: ["M5 4h14a2 2 0 0 1 2 2v14H3V6a2 2 0 0 1 2-2Z", "M7 2v4m10-4v4M3 9h18m-14 4h3m4 0h3m-10 4h3"],
  people: ["M8 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm8 0a3 3 0 1 0 0-6", "M3 21c.4-4 2.2-6 5-6s4.6 2 5 6m1-5c3 0 5 1.7 5 5"],
  route: ["M5 5a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm14 18a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z", "M5 5v3c0 3 2 4 5 4h4c3 0 5 1 5 4v3"],
  book: ["M4 5.5A3.5 3.5 0 0 1 7.5 2H11v17H7.5A3.5 3.5 0 0 0 4 22V5.5Z", "M20 5.5A3.5 3.5 0 0 0 16.5 2H13v17h3.5A3.5 3.5 0 0 1 20 22V5.5Z"],
  building: ["M4 21V8l8-5 8 5v13M2 21h20", "M8 10h1m6 0h1m-8 4h1m6 0h1m-8 4h8"],
  crown: ["m3 7 4 4 5-7 5 7 4-4-2 11H5L3 7Z", "M6 18h12"],
  location: ["M12 22s7-6.1 7-13a7 7 0 1 0-14 0c0 6.9 7 13 7 13Z", "M12 12a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"],
};

function CommunityIcon({ name }: { name: CommunityIconName }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      {iconPaths[name].map((path) => <path d={path} key={path} />)}
    </svg>
  );
}

const foundations = [
  { icon: "campus" as const, title: "Üniversiteyi Deneyimle", text: "Kampüsleri, amfileri ve çalışma kültürünü hedefin daha uzaktayken yakından tanı.", label: "Kampüs keşifleri" },
  { icon: "coffee" as const, title: "Koçlarınla Buluş", text: "Ekranın ötesine geçen sohbetlerde deneyimlerini dinle, sorularını doğrudan paylaş.", label: "Yüz yüze buluşmalar" },
  { icon: "profession" as const, title: "Mesleğini Yakından Tanı", text: "Farklı meslek alanlarını ve profesyonel çalışma ortamlarını karar vermeden önce gözlemle.", label: "Meslek keşfi" },
  { icon: "spark" as const, title: "Özel Deneyimlere Katıl", text: "İlham veren buluşmalar ve gelişim odaklı içeriklerle hazırlık sürecini zenginleştir.", label: "Topluluk deneyimleri" },
];

const experiences = [
  { eyebrow: "Kampüs günü", title: "Sınavdan Önce O Havayı Solu", text: "Hedeflediğin üniversitelerin gündelik ritmini, çalışma alanlarını ve öğrenci yaşamını koçların rehberliğinde keşfet.", icon: "campus" as const },
  { eyebrow: "Meslek keşfi", title: "Profesyonel Deneyim İçin İlk Adım", text: "İlgi duyduğun alanların çalışma ortamlarını tanı; bölüm ve meslek seçimine daha bilinçli yaklaş.", icon: "profession" as const },
  { eyebrow: "İlham buluşması", title: "Özel Konferanslar ve Networking", text: "Aynı hedef duygusunu taşıyan öğrenciler, koçlar ve davetli profesyonellerle bir araya gel.", icon: "people" as const },
];

const tiers = [
  { level: "Seviye 01", title: "Explorer", subtitle: "Kampüs Kaşifi", icon: "route" as const, text: "Topluluk deneyimlerini keşfetmeye başlayan öğrenciler için planlanan giriş seviyesi." },
  { level: "Seviye 02", title: "Scholar", subtitle: "Strateji Ustası", icon: "book" as const, text: "Düzenli gelişim ve çalışma kültürünü görünür kılmayı amaçlayan planlanan seviye." },
  { level: "Seviye 03", title: "Fellow", subtitle: "Geleceğin Profesyoneli", icon: "building" as const, text: "Meslek ve üniversite deneyimlerine odaklanan planlanan gelişim basamağı." },
  { level: "Seviye 04", title: "Ambassador", subtitle: "Uniform Elçisi", icon: "crown" as const, text: "Topluluk katkısını temsil etmek üzere tasarlanan planlanan üst seviye.", featured: true },
];

const events = [
  { category: "Kampüs ziyareti", title: "Üniversite Kampüsü ve Kütüphane Deneyimi", text: "Kampüs yaşamını ve akademik çalışma ortamlarını yerinde tanımaya yönelik örnek etkinlik formatı." },
  { category: "Profesyonel gün", title: "Meslek ve Ofis Gözlem Günü", text: "Farklı kariyer alanlarını profesyonellerden dinlemeye yönelik örnek deneyim formatı." },
  { category: "Koç buluşması", title: "Derece Koçlarıyla Strateji Buluşması", text: "Sınav temposu, deneme analizi ve hedef yönetimi üzerine planlanan örnek topluluk buluşması." },
];

export function CommunityPage() {
  useEffect(() => {
    const previousTitle = document.title;
    document.title = "Uniform Community | Uniform Akademi";
    return () => { document.title = previousTitle; };
  }, []);

  return (
    <div className="community-page">
      <section className="community-hero" aria-labelledby="community-page-title">
        <div className="community-ambient" aria-hidden="true" />
        <div className="community-container">
          <div className="community-hero__copy">
            <h1 id="community-page-title">YKS’ye hazırlanırken,<br /><em>üniversite hayatını</em> yaşamaya başla.</h1>
            <p>Uniform Community; hazırlık yolculuğunu kampüs, meslek ve insan deneyimleriyle zenginleştirmek için tasarlanan topluluk alanıdır.</p>
            <div className="community-actions">
              <a className="community-button community-button--navy" href="#community-nedir">Community’yi Keşfet <span aria-hidden="true">→</span></a>
              <a className="community-button community-button--outline" href="#nasil-katilirim">Nasıl katılabilirim?</a>
            </div>
          </div>
          <div className="community-hero__media">
            <img src="/images/home/community-campus.jpg" alt="Kampüs manzaralı bir kütüphanede birlikte çalışan üniversite öğrencileri" fetchPriority="high" />
            <div className="community-hero__overlay">
              <p>Geleceğini yerinde tanı</p>
              <h2>Kampüs atmosferi, gerçek deneyimler</h2>
            </div>
            <div className="community-hero__note"><CommunityIcon name="campus" /><span><small>Planlanan deneyim</small>Kampüs ve meslek keşifleri</span></div>
          </div>
        </div>
      </section>

      <section className="community-section community-section--white" id="community-nedir" aria-labelledby="community-what-title">
        <div className="community-container">
          <div className="community-heading community-heading--center">
            <p>Vizyon &amp; felsefe</p>
            <h2 id="community-what-title">Çünkü üniversite sadece kazanılacak bir hedef değil, yaşanacak bir deneyim.</h2>
            <span>Community, sınav hazırlığını geleceğinle kurduğun somut bağlarla besleyen planlı bir deneyim alanı olarak kurgulanıyor.</span>
          </div>
          <div className="community-foundations">
            {foundations.map((item) => (
              <article key={item.title}>
                <div className="community-icon"><CommunityIcon name={item.icon} /></div>
                <h3>{item.title}</h3><p>{item.text}</p><small>{item.label} <span aria-hidden="true">→</span></small>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="community-section community-purpose" aria-labelledby="community-purpose-title">
        <div className="community-container community-purpose__layout">
          <div className="community-heading">
            <p>Psikoloji &amp; disiplin</p>
            <h2 id="community-purpose-title">Soyut bir hedefi <em>somut bir gerçeğe</em> dönüştürmek.</h2>
            <span>Uzun bir hazırlık döneminde hedefini zihninde canlı tutmak zor olabilir. Community, ulaşmak istediğin hayatla bugünden bağ kurmanı ve çalışma nedenini hatırlamanı desteklemek için tasarlanıyor.</span>
          </div>
          <div className="community-purpose__steps" aria-label="Deneyimden disipline gelişim döngüsü">
            {["Deneyim", "Bağ kur", "Motivasyon", "Disiplin"].map((step, index) => <div key={step}><strong>0{index + 1}</strong><span>{step}</span></div>)}
            <p><span aria-hidden="true">◆</span> Deneyim motivasyonu, motivasyon disiplini besler.</p>
          </div>
        </div>
      </section>

      <section className="community-section community-section--white" aria-labelledby="community-experiences-title">
        <div className="community-container">
          <div className="community-heading community-heading--split"><div><p>Birlikte keşfet</p><h2 id="community-experiences-title">Community Deneyimleri</h2></div><span>Etkinlikler, uygun program ve iş birlikleri oluştukça duyurulacak planlanan topluluk deneyimleridir.</span></div>
          <div className="community-experiences">
            {experiences.map((item, index) => (
              <article className={index === 2 ? "community-experience community-experience--dark" : "community-experience"} key={item.title}>
                <div className="community-icon"><CommunityIcon name={item.icon} /></div><small>{item.eyebrow}</small><h3>{item.title}</h3><p>{item.text}</p><span>Planlanıyor</span>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="community-giveback" aria-labelledby="community-giveback-title">
        <div className="community-container">
          <div className="community-giveback__intro">
            <p>Başarıya yatırım</p>
            <h2 id="community-giveback-title">İlk 5.000’e gir.<br /><em>Ödediğini geri al.</em></h2>
            <span>Uniform Akademi’nin planlanan Give Back başarı desteği</span>
          </div>
          <div className="community-giveback__rule">
            <p>Give Back nasıl planlanıyor?</p>
            <h3><strong>Sınava Kadar</strong> paketini satın alan ve YKS’de Türkiye genelinde <strong>ilk 5.000</strong> içinde yer alan öğrencilerin Uniform Akademi’ye ödediği tutarın iade edilmesi planlanmaktadır. Dil (YDT) alanından sınava giren öğrenciler Give Back programına dahil değildir.</h3>
            <div><span>Ödeme zamanı<strong>Takip eden yıl</strong></span><i aria-hidden="true">→</i><span>Ödeme planı<strong>12 aylık ödeme</strong></span></div>
          </div>
          <div className="community-giveback__steps">
            <article><strong>01</strong><h3>Sınava Kadar Paketini Seç</h3><p>Give Back planı yalnızca Sınava Kadar paketi için geçerlidir.</p></article>
            <article><strong>02</strong><h3>İlk 5.000’e Gir</h3><p>YKS sıralamanda Türkiye genelinde ilk 5.000 içinde yer al.</p></article>
            <article><strong>03</strong><h3>12 Ayda Geri Al</h3><p>Ödediğin tutarın takip eden yıl boyunca 12 aylık ödemeye bölünmesi planlanır.</p></article>
          </div>
          <div className="community-giveback__actions"><Link className="community-button community-button--gold" to="/kocluk">Sınava Kadar Paketini İncele</Link></div>
          <p className="community-giveback__fineprint">Give Back, planlanan ayrı bir başarı desteğidir; normal paket iptal ve iade politikasından ayrıdır ve Dil (YDT) alanından sınava giren öğrencileri kapsamaz.</p>
        </div>
      </section>

      <section className="community-section community-section--white community-connection" aria-labelledby="community-connection-title">
        <div className="community-container">
          <div className="community-connection__panel">
            <div className="community-heading community-heading--center"><p>Bağlantı kur</p><h2 id="community-connection-title">Süreç Deneyimi + Somut Sonuç</h2></div>
            <div className="community-connection__grid">
              <article><span>01</span><h3>Community</h3><p>Hazırlık sürecinde aidiyeti, üniversite vizyonunu ve birlikte gelişme kültürünü güçlendirmeyi amaçlar.</p><small>Süreç odaklı değer</small></article>
              <article><span>02</span><h3>Give Back</h3><p>Sınava Kadar paketi alan, Dil (YDT) alanı dışında YKS’de ilk 5.000’e giren öğrencilerin başarısını maddi olarak desteklemeyi amaçlar.</p><small>Sonuç odaklı değer</small></article>
            </div>
            <blockquote>“Sen geleceğine yatırım yaparken, biz de sana yatırım yapıyoruz.”</blockquote>
          </div>
        </div>
      </section>

      <section className="community-section" id="nasil-katilirim" aria-labelledby="community-join-title">
        <div className="community-container">
          <div className="community-heading community-heading--center"><p>Katılım yolu</p><h2 id="community-join-title">Topluluk kapılarına giden yol</h2><span>Community üyelik altyapısı henüz aktif değil. Deneyim programı açıldığında güncel katılım ayrıntıları bu sayfada duyurulacak.</span></div>
          <div className="community-join-grid">
            <article><strong>Adım 01</strong><h3>Koçluğu Keşfet</h3><p>Uniform’un mevcut koçluk yaklaşımını ve sana uygun paket seçeneklerini incele.</p></article>
            <article><strong>Adım 02</strong><h3>Sürecini Başlat</h3><p>Hedeflerine uygun birebir mentörlük yolculuğuna gerçek kayıt akışı üzerinden başla.</p></article>
            <article><strong>Adım 03</strong><h3>Duyuruları Takip Et</h3><p>Community katılımı ve planlanan etkinlikler açıldığında yayınlanacak güncel bilgileri takip et.</p></article>
          </div>
          <div className="community-centered-action"><Link className="community-button community-button--navy" to="/kocluk">Koçluğu İncele <span aria-hidden="true">→</span></Link></div>
        </div>
      </section>

      <section className="community-section community-section--white" aria-labelledby="community-tiers-title">
        <div className="community-container">
          <div className="community-heading community-heading--center"><p>Planlanan gelişim modeli</p><h2 id="community-tiers-title">Seviyeler ve Rozetler</h2><span>Bu seviyeler henüz kazanım veya hesap özelliği değildir; Community vizyonundaki gelişim modelini gösterir.</span></div>
          <div className="community-tiers">
            {tiers.map((tier) => <article className={tier.featured ? "community-tier community-tier--featured" : "community-tier"} key={tier.title}><div className="community-tier__icon"><CommunityIcon name={tier.icon} /></div><small>{tier.level}</small><h3>{tier.title}</h3><strong>{tier.subtitle}</strong><p>{tier.text}</p></article>)}
          </div>
        </div>
      </section>

      <section className="community-section community-events" aria-labelledby="community-events-title">
        <div className="community-container">
          <div className="community-heading community-heading--split"><div><p>Takvim &amp; buluşmalar</p><h2 id="community-events-title">Yaklaşan Deneyimler ve Etkinlikler</h2></div><span className="community-status">Program hazırlanıyor</span></div>
          <p className="community-events__notice">Aşağıdaki kartlar planlanan etkinlik formatlarını örnekler. Tarih ve kayıt sistemi henüz yayınlanmamıştır.</p>
          <div className="community-events__grid">
            {events.map((event) => <article key={event.title}><div><span>{event.category}</span><small>Örnek içerik</small></div><h3>{event.title}</h3><p>{event.text}</p><button type="button" disabled aria-label={`${event.title} için kayıt henüz açılmadı`}><CommunityIcon name="calendar" /> Kayıt henüz açılmadı</button></article>)}
          </div>
        </div>
      </section>

      <section className="community-final" aria-labelledby="community-final-title">
        <div className="community-container">
          <p>Gelecek şimdi başlar</p><h2 id="community-final-title">Bugün sınava hazırlanıyorsun.<br /><em>Yarın o hayatın içinde olacaksın.</em></h2><span>Hedefine doğru ilerlerken doğru koçla çalış, üniversite ve meslek dünyasını daha yakından tanımaya hazırlan.</span>
          <div className="community-actions"><Link className="community-button community-button--navy" to="/coaches">Koçları Keşfet</Link><Link className="community-button community-button--gold" to="/kocluk">Uniform ile Hazırlanmaya Başla</Link></div>
        </div>
      </section>
    </div>
  );
}
