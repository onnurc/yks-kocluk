import { useEffect } from "react";
import { Link } from "react-router-dom";
import { BrandLogo } from "../public/BrandLogo";
import "./full-page-error.css";

export type FullPageErrorAction = {
  label: string;
  to?: string;
  href?: string;
  onClick?: () => void;
};

type FullPageErrorProps = {
  code: string;
  eyebrow: string;
  title: string;
  description: string;
  primaryAction: FullPageErrorAction;
  secondaryAction?: FullPageErrorAction;
};

function ErrorAction({ action, secondary = false }: { action: FullPageErrorAction; secondary?: boolean }) {
  const className = `uniform-error-page__action ${secondary ? "uniform-error-page__action--secondary" : "uniform-error-page__action--primary"}`;

  if (action.to) {
    return <Link className={className} to={action.to}>{action.label}</Link>;
  }

  if (action.href) {
    return <a className={className} href={action.href}>{action.label}</a>;
  }

  return <button className={className} type="button" onClick={action.onClick}>{action.label}</button>;
}

export function FullPageError({
  code,
  eyebrow,
  title,
  description,
  primaryAction,
  secondaryAction,
}: FullPageErrorProps) {
  useEffect(() => {
    const previousTitle = document.title;
    document.title = `${code} | Uniform Akademi`;
    return () => {
      document.title = previousTitle;
    };
  }, [code]);

  return (
    <div className="uniform-error-page">
      <header className="uniform-error-page__header">
        <BrandLogo size="compact" />
        <span className="uniform-error-page__product">YKS Mentörlük</span>
      </header>

      <main className="uniform-error-page__main" aria-labelledby="uniform-error-title">
        <section className="uniform-error-page__card">
          <div className="uniform-error-page__accent" aria-hidden="true"><span /></div>
          <p className="uniform-error-page__eyebrow">{eyebrow}</p>
          <p className="uniform-error-page__code" aria-label={`Hata kodu ${code}`}>{code}</p>
          <h1 id="uniform-error-title">{title}</h1>
          <p className="uniform-error-page__description">{description}</p>
          <div className="uniform-error-page__actions">
            <ErrorAction action={primaryAction} />
            {secondaryAction && <ErrorAction action={secondaryAction} secondary />}
          </div>
        </section>
      </main>

      <footer className="uniform-error-page__footer">Uniform Akademi</footer>
    </div>
  );
}
