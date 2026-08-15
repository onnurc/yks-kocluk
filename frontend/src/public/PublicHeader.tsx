import { useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import { BrandLogo } from "./BrandLogo";
import { publicNavigation } from "./publicSiteConfig";
import { useAuth } from "../auth/AuthProvider";
import { readinessPathForUser } from "../auth/authNavigation";

function MenuIcon({ open }: { open: boolean }) {
  return open ? (
    <svg aria-hidden="true" viewBox="0 0 24 24">
      <path d="m6 6 12 12M18 6 6 18" />
    </svg>
  ) : (
    <svg aria-hidden="true" viewBox="0 0 24 24">
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  );
}

export function PublicHeader() {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const { user, isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  const closeMenu = () => setMenuOpen(false);

  const handleKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === "Escape" && menuOpen) {
      closeMenu();
      menuButtonRef.current?.focus();
    }
  };

  const handleLogout = async () => {
    closeMenu();
    await logout();
    navigate("/login");
  };

  const panelPath = user ? readinessPathForUser(user) : "/dashboard";

  return (
    <header className="public-header" onKeyDown={handleKeyDown}>
      <div className="public-header__inner">
        <BrandLogo to="/" />

        <nav className="public-header__desktop-nav" aria-label="Ana menü">
          {publicNavigation.map((item) =>
            item.available ? (
              <NavLink
                className={({ isActive }) => `public-nav-link${isActive ? " public-nav-link--active" : ""}`}
                key={item.label}
                to={item.to}
              >
                {item.label}
              </NavLink>
            ) : (
              <span className="public-nav-link public-nav-link--pending" key={item.label} aria-disabled="true">
                {item.label}
              </span>
            ),
          )}
        </nav>

        <div className="public-header__actions">
          <div className="public-header__tablet-actions">
            {isAuthenticated ? (
              <>
                <Link className="public-button public-button--dark" to={panelPath}>
                  Panele Git
                </Link>
                <button className="public-button public-button--gold public-header__consultation" type="button" onClick={handleLogout}>
                  Çıkış Yap
                </button>
              </>
            ) : (
              <>
                <Link className="public-button public-button--dark" to="/login">
                  Giriş Yap
                </Link>
                <Link className="public-button public-button--gold public-header__consultation" to="/register">
                  Ücretsiz Görüş
                </Link>
              </>
            )}
          </div>

          <div className="public-header__desktop-actions">
            {isAuthenticated ? (
              <>
                <Link className="public-button public-button--dark" to={panelPath}>
                  Panele Git
                </Link>
                <button className="public-button public-button--gold" type="button" onClick={handleLogout}>
                  Çıkış Yap
                </button>
                <Link className="public-header__account" to={panelPath} aria-label={user?.fullName ?? "Hesabım"}>
                  <svg viewBox="0 0 24 24">
                    <path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 8c.55-3.36 3.46-5.5 7-5.5s6.45 2.14 7 5.5H5Z" />
                  </svg>
                </Link>
              </>
            ) : (
              <>
                <Link className="public-button public-button--dark" to="/login">
                  Giriş Yap
                </Link>
                <Link className="public-button public-button--gold" to="/register">
                  Ücretsiz Görüş
                </Link>
                <span className="public-header__account" aria-hidden="true">
                  <svg viewBox="0 0 24 24">
                    <path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 8c.55-3.36 3.46-5.5 7-5.5s6.45 2.14 7 5.5H5Z" />
                  </svg>
                </span>
              </>
            )}
          </div>

          <button
            ref={menuButtonRef}
            className="public-header__menu-button"
            type="button"
            aria-controls="public-mobile-menu"
            aria-expanded={menuOpen}
            aria-label={menuOpen ? "Menüyü kapat" : "Menüyü aç"}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <MenuIcon open={menuOpen} />
          </button>
        </div>
      </div>

      {menuOpen && (
        <div className="public-mobile-menu" id="public-mobile-menu" role="dialog" aria-label="Mobil menü">
          <nav className="public-mobile-menu__nav" aria-label="Mobil ana menü">
            {publicNavigation.map((item) =>
              item.available ? (
                <Link className="public-mobile-menu__link" key={item.label} to={item.to} onClick={closeMenu}>
                  {item.label}
                </Link>
              ) : (
                <span className="public-mobile-menu__link public-mobile-menu__link--pending" key={item.label} aria-disabled="true">
                  {item.label}
                  <small>Yakında</small>
                </span>
              ),
            )}
          </nav>
          <div className="public-mobile-menu__actions">
            {isAuthenticated ? (
              <>
                <Link className="public-button public-button--dark" to={panelPath} onClick={closeMenu}>
                  Panele Git
                </Link>
                <button className="public-button public-button--gold" type="button" onClick={handleLogout}>
                  Çıkış Yap
                </button>
              </>
            ) : (
              <>
                <Link className="public-button public-button--dark" to="/login" onClick={closeMenu}>
                  Giriş Yap
                </Link>
                <Link className="public-button public-button--gold" to="/register" onClick={closeMenu}>
                  Ücretsiz Görüş
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  );
}
