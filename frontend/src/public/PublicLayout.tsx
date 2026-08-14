import { Outlet } from "react-router-dom";
import { PublicFooter } from "./PublicFooter";
import { PublicHeader } from "./PublicHeader";
import "./public-site.css";

export function PublicLayout() {
  return (
    <div className="public-site">
      <a className="public-site__skip-link" href="#public-main">
        Ana içeriğe geç
      </a>
      <PublicHeader />
      <main className="public-site__main" id="public-main">
        <Outlet />
      </main>
      <PublicFooter />
    </div>
  );
}
