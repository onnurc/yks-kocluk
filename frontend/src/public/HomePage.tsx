import { useEffect } from "react";
import { Link } from "react-router-dom";
import "./home-page.css";

const communityFeatures = [
  { icon: "campus", title: "Kampüs Deneyimleri", text: "Hedefindeki üniversitenin havasını erkenden solu." },
  { icon: "people", title: "Mentor Günleri", text: "Birebir tecrübe aktarımı ve strateji seansları." },
  { icon: "network", title: "Bağlantı Ağı", text: "Seninle aynı vizyonu paylaşan öğrencilerle tanış." },
  { icon: "compass", title: "Üniversite Gezileri", text: "Motivasyon artırıcı özel kampüs turları." },
] as const;

const reasons = [
  { icon: "mind", title: "Mentörlük", text: "Derece yapmış uzman mentörlerle haftalık strateji toplantıları." },
  { icon: "chart", title: "Sıkı Takip", text: "Günlük çalışma analizi ve detaylı deneme sınavı raporlaması." },
  { icon: "route", title: "Kişisel Yol Haritası", text: "Sadece senin öğrenme hızına ve hedeflerine özel program." },
  { icon: "community", title: "Topluluk", text: "Motivasyonunu her an yüksek tutacak özel öğrenci ağı." },
] as const;

const plans = [
  {
    name: "Aylık Kontrol",
    popular: false,
    features: ["Haftalık birebir görüşme", "Kişisel çalışma programı", "Düzenli ilerleme takibi"],
  },
  {
    name: "3 Aylık Hızlandırma",
    popular: true,
    features: ["Yoğunlaştırılmış mentör desteği", "Detaylı deneme analizi", "Uniform Topluluğu erişimi"],
  },
  {
    name: "Uzun Dönem Programı",
    popular: false,
    features: ["Uzun vadeli hedef planı", "Düzenli mentör görüşmeleri", "Kişiselleştirilmiş takip"],
  },
] as const;

const faqItems = [
  {
    question: "Mentörler kimlerden oluşuyor?",
    answer:
      "Mentörlerimiz, güçlü akademik geçmişe sahip ve öğrenci rehberliği için özel eğitimlerden geçen üniversite öğrencileri ile mezunlardan oluşur.",
  },
  {
    question: "Görüşmeler nasıl gerçekleştiriliyor?",
    answer:
      "Görüşmeler çevrim içi gerçekleştirilir. Belirlenen gün ve saatte mentörünle birebir değerlendirme ve planlama seansı yapılır.",
  },
  {
    question: "Programı istediğim zaman sonlandırabilir miyim?",
    answer:
      "Program süresi ve sonlandırma koşulları seçilen pakete göre değişir. Güncel koşullar kayıt öncesinde açıkça paylaşılır.",
  },
] as const;

function HomeIcon({ name }: { name: string }) {
  const paths: Record<string, string> = {
    campus: "M3 10 12 4l9 6-9 6-9-6Zm3 3v5m4-3v5m4-5v5m4-7v5M4 20h16",
    people: "M8 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm8-1a2.5 2.5 0 1 0 0-5M3 20c.4-4 2.4-6 5-6s4.6 2 5 6m1-6c3 0 5 2 5 5",
    network: "M12 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM5 21a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm14 0a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM10 7 6.5 15m7.5-8 3.5 8M8 18h8",
    compass: "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Zm3.8-13.8-2.1 5.5-5.5 2.1 2.1-5.5 5.5-2.1Z",
    mind: "M9 19v-2.2A7 7 0 1 1 18 10c0 2.7-1.4 4.3-3 5.7V19M9 22h6",
    chart: "M4 20V10m6 10V4m6 16v-7m4 7H2",
    route: "M5 5a2 2 0 1 0 0-4 2 2 0 0 0 0 4Zm14 18a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM5 5v3c0 3 2 4 5 4h4c3 0 5 1 5 4v3",
    community: "M8 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm8 0a3 3 0 1 0 0-6m-13 10c.5-4 2.2-6 5-6s4.5 2 5 6m1-5c2.5 0 4.5 1.6 5 5",
  };

  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d={paths[name] ?? paths.community} />
    </svg>
  );
}

export function HomePage() {
  useEffect(() => {
    const previousTitle = document.title;
    document.title = "Uniform Akademi | YKS Mentörlük";
    return () => {
      document.title = previousTitle;
    };
  }, []);

  return (
    <div className="home-page">
      <section className="home-hero" aria-labelledby="home-title">
        <div className="home-hero__pattern" aria-hidden="true" />
        <div className="home-hero__inner">
          <p className="home-eyebrow"><span /> YKS Mentörlük Programı</p>
          <h1 id="home-title">
            Hedeflerin Kadar <em>Disiplinli</em>,<br />Başarın Kadar Kalıcı.
          </h1>
          <p className="home-hero__lead">
            Türkiye'nin prestijli üniversitelerinde okuyan deneyimli mentörlerle hedefine giden stratejik yolu birlikte çiz.
          </p>
          <div className="home-hero__actions">
            <Link className="home-button home-button--primary" to="/register">Ücretsiz Görüşme Ayarla</Link>
            <a className="home-button home-button--outline" href="#neden-uniform">Sistemimizi İncele</a>
          </div>
        </div>
      </section>

      <section className="home-community" aria-labelledby="community-title">
        <div className="home-container">
          <div className="home-community__media">
            <img src="/images/home/community-campus.jpg" alt="Aydınlık bir kampüs ortamında birlikte çalışan öğrenciler" fetchPriority="high" />
            <button type="button" disabled aria-label="Tanıtım videosu henüz kullanıma hazır değil"><span aria-hidden="true">▶</span></button>
          </div>
          <div className="home-community__content">
            <div className="home-community__intro">
              <h2 id="community-title">Uniform Topluluğu</h2>
              <p>Sadece ders çalışmakla kalma, başarı odaklı bir ekosistemin parçası ol. Türkiye'nin iyi üniversitelerine giden yolda sana eşlik edecek bir topluluk inşa ettik.</p>
              <span aria-hidden="true" />
            </div>
            <div className="home-community__features">
              {communityFeatures.map((feature) => (
                <article key={feature.title}>
                  <div className="home-icon"><HomeIcon name={feature.icon} /></div>
                  <h3>{feature.title}</h3>
                  <p>{feature.text}</p>
                </article>
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="home-reasons" id="neden-uniform" aria-labelledby="reasons-title">
        <div className="home-container">
          <div className="home-section-heading">
            <h2 id="reasons-title">Neden Uniform?</h2>
            <p>Sıradan bir koçluk değil, veri odaklı ve tamamen kişiselleştirilmiş bir başarı mühendisliği.</p>
          </div>
          <div className="home-reasons__grid">
            {reasons.map((reason) => (
              <article key={reason.title}>
                <div className="home-icon home-icon--plain"><HomeIcon name={reason.icon} /></div>
                <h3>{reason.title}</h3>
                <p>{reason.text}</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="home-plans" aria-labelledby="plans-title">
        <div className="home-container">
          <div className="home-section-heading">
            <h2 id="plans-title">Sana Uygun Planı Seç</h2>
            <p>Hedefine ulaşmak için doğru zaman, doğru strateji.</p>
          </div>
          <div className="home-plans__grid">
            {plans.map((plan) => (
              <article className={plan.popular ? "home-plan home-plan--featured" : "home-plan"} key={plan.name}>
                {plan.popular && <span className="home-plan__badge">Öne Çıkan</span>}
                <h3>{plan.name}</h3>
                <p className="home-plan__price">Güncel fiyat için kayıt olun</p>
                <ul>{plan.features.map((feature) => <li key={feature}>{feature}</li>)}</ul>
                <Link to="/register">Bilgi Al</Link>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="home-success" aria-labelledby="success-title">
        <div className="home-container home-success__layout">
          <div className="home-success__intro">
            <h2 id="success-title">Onlar Başardı,<br />Sıra Sende.</h2>
            <p>Uniform öğrencilerinin doğrulanmış başarı hikâyeleri yayınlandığında burada paylaşılacak.</p>
            <Link to="/register">Kendi yolculuğunu başlat</Link>
          </div>
          <div className="home-success__cards" aria-label="Başarı hikâyeleri">
            {["Hedef", "Disiplin", "Gelişim"].map((title) => (
              <article key={title}>
                <div className="home-success__placeholder" aria-hidden="true"><HomeIcon name="community" /></div>
                <p className="home-success__label">{title}</p>
                <h3>Başarı hikâyesi yakında</h3>
                <p>Doğrulanmış öğrenci deneyimleri hazırlanıyor.</p>
              </article>
            ))}
          </div>
        </div>
      </section>

      <section className="home-faq" aria-labelledby="faq-title">
        <div className="home-faq__inner">
          <h2 id="faq-title">Sıkça Sorulan Sorular</h2>
          <div className="home-faq__list">
            {faqItems.map((item) => (
              <details key={item.question}>
                <summary><span>{item.question}</span><span aria-hidden="true">+</span></summary>
                <p>{item.answer}</p>
              </details>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
