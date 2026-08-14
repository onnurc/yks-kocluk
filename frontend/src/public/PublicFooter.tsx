import { Link } from "react-router-dom";
import { BrandLogo } from "./BrandLogo";
import { publicQuickLinks, publicSiteConfig } from "./publicSiteConfig";

export function PublicFooter() {
  return (
    <footer className="public-footer">
      <div className="public-footer__inner">
        <section className="public-footer__brand" aria-label="Uniform Akademi hakkında">
          <BrandLogo size="compact" to="/" />
          <p>{publicSiteConfig.description}</p>
        </section>

        <nav className="public-footer__section" aria-label="Hızlı erişim">
          <h2>Hızlı Erişim</h2>
          <ul>
            {publicQuickLinks.map((item) => (
              <li key={item.label}>
                {item.available ? (
                  <Link to={item.to}>{item.label}</Link>
                ) : (
                  <span aria-disabled="true">{item.label}</span>
                )}
              </li>
            ))}
          </ul>
        </nav>

        <section className="public-footer__section public-footer__contact">
          <h2>İletişim</h2>
          <p>{publicSiteConfig.contactPlaceholder}</p>
        </section>
      </div>

      <div className="public-footer__bottom">
        <p>© {new Date().getFullYear()} Uniform Akademi. Tüm hakları saklıdır.</p>
      </div>
    </footer>
  );
}
