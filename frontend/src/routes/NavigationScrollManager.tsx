import { useEffect, useLayoutEffect } from "react";
import { useLocation, useNavigationType } from "react-router-dom";

type ScrollPosition = { left: number; top: number };

const scrollPositions = new Map<string, ScrollPosition>();

function scrollToHash(hash: string) {
  if (!hash) {
    return false;
  }

  let id: string;
  try {
    id = decodeURIComponent(hash.slice(1));
  } catch {
    id = hash.slice(1);
  }

  const target = document.getElementById(id);
  target?.scrollIntoView({ behavior: "auto", block: "start" });
  return Boolean(target);
}

export function NavigationScrollManager() {
  const location = useLocation();
  const navigationType = useNavigationType();

  useEffect(() => {
    const previousScrollRestoration = window.history.scrollRestoration;
    window.history.scrollRestoration = "manual";

    return () => {
      window.history.scrollRestoration = previousScrollRestoration;
    };
  }, []);

  useEffect(() => {
    const handleSameLocationHashClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }

      const anchor = (event.target as Element | null)?.closest("a");
      const href = anchor?.getAttribute("href");
      if (!href || !href.includes("#")) {
        return;
      }

      const currentUrl = new URL(`${location.pathname}${location.search}${location.hash}`, window.location.origin);
      const targetUrl = new URL(href, currentUrl);
      if (
        targetUrl.pathname === location.pathname
        && targetUrl.search === location.search
        && targetUrl.hash === location.hash
      ) {
        window.requestAnimationFrame(() => scrollToHash(targetUrl.hash));
      }
    };

    document.addEventListener("click", handleSameLocationHashClick);
    return () => document.removeEventListener("click", handleSameLocationHashClick);
  }, [location.hash, location.pathname, location.search]);

  useLayoutEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      const savedPosition = scrollPositions.get(location.key);

      if (navigationType === "POP" && savedPosition) {
        window.scrollTo({ ...savedPosition, behavior: "auto" });
        return;
      }

      if (!scrollToHash(location.hash)) {
        window.scrollTo({ left: 0, top: 0, behavior: "auto" });
      }
    });

    return () => {
      window.cancelAnimationFrame(frame);
      scrollPositions.set(location.key, { left: window.scrollX, top: window.scrollY });
    };
  }, [location.hash, location.key, navigationType]);

  return null;
}
