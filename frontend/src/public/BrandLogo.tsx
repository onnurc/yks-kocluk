import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { publicSiteConfig } from "./publicSiteConfig";

type BrandLogoProps = {
  className?: string;
  size?: "compact" | "default";
  to?: string;
};

export function BrandLogo({ className = "", size = "default", to }: BrandLogoProps) {
  const content: ReactNode = (
    <>
      <svg
        aria-hidden="true"
        className="brand-logo__mark"
        viewBox="0 0 40 40"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path d="M10 8.5v12.25C10 27.52 14.16 32 20 32s10-4.48 10-11.25V8.5h-5.25v12.1c0 3.98-1.77 6.4-4.75 6.4s-4.75-2.42-4.75-6.4V8.5H10Z" />
        <path d="m31.5 4 .96 2.54L35 7.5l-2.54.96L31.5 11l-.96-2.54L28 7.5l2.54-.96L31.5 4Z" />
      </svg>
      <span className="brand-logo__wordmark">{publicSiteConfig.brandName}</span>
    </>
  );
  const classes = `brand-logo brand-logo--${size} ${className}`.trim();

  if (to) {
    return (
      <Link className={classes} to={to} aria-label={`${publicSiteConfig.accessibleBrandName} ana sayfa`}>
        {content}
      </Link>
    );
  }

  return (
    <div className={classes} aria-label={publicSiteConfig.accessibleBrandName}>
      {content}
    </div>
  );
}
