import type { ReactNode } from "react";
import { BrandLogo } from "../public/BrandLogo";

type AuthPageShellProps = {
  title: string;
  lead?: string;
  icon?: ReactNode;
  children: ReactNode;
  wide?: boolean;
  compact?: boolean;
};

export function AuthPageShell({ title, lead, icon, children, wide = false, compact = false }: AuthPageShellProps) {
  return (
    <div className="auth-shell">
      <a className="auth-skip-link" href="#auth-main">Ana içeriğe geç</a>
      <div className="auth-shell__brand"><BrandLogo to="/" size="compact" /></div>
      <main className="auth-page" id="auth-main">
        <section className={`auth-card${wide ? " auth-card--wide" : ""}${compact ? " auth-card--compact" : ""}`} aria-labelledby="auth-title">
          <header className="auth-card__header">
            {icon != null && <span className="auth-card__icon" aria-hidden="true">{icon}</span>}
            <h1 id="auth-title">{title}</h1>
            {lead && <p className="auth-lead">{lead}</p>}
          </header>
          {children}
        </section>
        <aside className="auth-community" aria-label="Uniform Community">
          <span className="auth-community__icon" aria-hidden="true">♣</span>
          <div>
            <h2>Uniform Community Seni Bekliyor</h2>
            <p>Hesabınla etkinliklere katıl, mentör görüşmelerini planla ve çalışma yolculuğunu tek yerden yönet.</p>
          </div>
        </aside>
      </main>
      <footer className="auth-shell__footer">
        <BrandLogo size="compact" />
        <p>© 2026 Uniform Akademi. Tüm hakları saklıdır.</p>
      </footer>
    </div>
  );
}
