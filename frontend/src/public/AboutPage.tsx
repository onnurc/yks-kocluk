import { useEffect } from "react";
import "./about-page.css";

const communityCards = [
  {
    title: "Ortak Hedefler",
    description:
      "Aynı vizyonu paylaşan öğrencilerle bir araya gelerek motivasyonunuzu sürekli yüksek tutun.",
    image: "/images/about/shared-goals.jpg",
    alt: "Bir çalışma planı üzerinde birlikte çalışan iki üniversite öğrencisi",
  },
  {
    title: "Karşılıklı Gelişim",
    description:
      "Sadece bireysel değil, topluluk olarak birlikte öğreniyor ve birbirimizi yukarı taşıyoruz.",
    image: "/images/about/mutual-growth.jpg",
    alt: "Kişiselleştirilmiş YKS çalışma planı ve dolma kalem",
  },
  {
    title: "Üniversite Atmosferi",
    description:
      "Daha kazanmadan önce, o havayı solumanızı sağlayan bir deneyim tasarlıyoruz.",
    image: "/images/about/university-atmosphere.jpg",
    alt: "Öğrencilerin bulunduğu geniş ve modern bir üniversite kampüsü",
  },
] as const;

function PlayIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 32 32">
      <path d="M10.5 7.5v17L24 16 10.5 7.5Z" />
    </svg>
  );
}

function CommunityIcon() {
  return (
    <svg aria-hidden="true" viewBox="0 0 32 32">
      <path d="M12 15a5 5 0 1 0 0-10 5 5 0 0 0 0 10Zm9-2a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM3 26c.62-5.05 4.15-8 9-8s8.38 2.95 9 8H3Zm17.15-9.58C25.07 16.9 28 19.48 29 24h-5.58a12.14 12.14 0 0 0-3.27-7.58Z" />
    </svg>
  );
}

export function AboutPage() {
  useEffect(() => {
    const previousTitle = document.title;
    document.title = "Biz Kimiz | Uniform Akademi";
    return () => {
      document.title = previousTitle;
    };
  }, []);

  return (
    <div className="about-page">
      <section className="about-hero" aria-labelledby="about-hero-title">
        <div className="about-hero__glow" aria-hidden="true" />
        <div className="about-hero__inner">
          <h1 id="about-hero-title">
            <span>Geleceği</span>{" "}
            <span>İnşa Eden</span>{" "}
            <span>Bir Vizyon.</span>
          </h1>
          <p className="about-hero__lead">
            Akademik mükemmelliği ve kişisel gelişimi bir araya getiren, öğrencilerin potansiyelini en üst düzeye çıkaran modern bir rehberlik ekosistemi.
          </p>
          <div className="about-hero__media">
            <img
              src="/images/about/hero-library.jpg"
              alt="Modern bir üniversite kütüphanesinde birlikte çalışan öğrenciler"
              fetchPriority="high"
            />
            <div className="about-hero__shade" aria-hidden="true" />
            <button
              className="about-hero__play"
              type="button"
              disabled
              aria-label="Tanıtım videosu henüz kullanıma hazır değil"
            >
              <PlayIcon />
            </button>
          </div>
        </div>
      </section>

      <section className="about-story" aria-labelledby="about-story-title">
        <div className="about-story__inner">
          <div className="about-section-label">
            <span aria-hidden="true" />
            <p>Hikâyemiz</p>
          </div>
          <h2 id="about-story-title">
            Bir Hayalden,
            <br />
            Güçlü Bir Ekosisteme.
          </h2>
          <div className="about-story__copy">
            <p>
              Mevcut eğitim sisteminin karmaşası içinde, öğrencilerin sadece bilgiye değil, aynı zamanda kendilerini anlayan, onlara yön veren ve ilham olan bir rehberliğe ihtiyaç duyduklarını fark ettik. Çoğu zaman potansiyel, yanlış yönlendirmeler veya eksik motivasyon nedeniyle kayboluyordu.
            </p>
            <p>
              Uniform, tam da bu eksikliği gidermek için kuruldu. Amacımız, sınav hazırlığını sıradan bir süreç olmaktan çıkarıp, prestijli ve dönüştürücü bir deneyime dönüştürmekti. Öğrencilere sadece ne çalışmaları gerektiğini söyleyen değil, nasıl başarılı olacaklarını öğreten, onlara vizyon katan bir ekosistem inşa etmeyi hayal ettik.
            </p>
            <p>
              Bugün, bu hayal, yüzlerce öğrencinin hayatına dokunan, onları hedeflerine ulaştıran ve akademik başarıyı bir yaşam tarzı haline getiren güçlü bir yapıya dönüştü.
            </p>
          </div>
        </div>
      </section>

      <section className="about-community" aria-labelledby="about-community-title">
        <div className="about-community__inner">
          <div className="about-community__heading">
            <h2 id="about-community-title">
              Sınavın Ötesinde,
              <br />
              Birlikte Büyüyen Bir Topluluk.
            </h2>
            <span className="about-community__icon">
              <CommunityIcon />
            </span>
          </div>
          <div className="about-community__grid">
            {communityCards.map((card) => (
              <article className="about-community-card" key={card.title}>
                <img src={card.image} alt={card.alt} loading="lazy" />
                <div className="about-community-card__shade" aria-hidden="true" />
                <div className="about-community-card__copy">
                  <h3>{card.title}</h3>
                  <p>{card.description}</p>
                </div>
              </article>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
