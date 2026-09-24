import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Link, MemoryRouter, Route, Routes, useNavigate } from "react-router-dom";
import { PublicFooter } from "../public/PublicFooter";
import { NavigationScrollManager } from "../routes/NavigationScrollManager";

let scrollY = 0;
const originalScrollIntoView = Element.prototype.scrollIntoView;

function NavigationFixture() {
  const navigate = useNavigate();

  return (
    <Routes>
      <Route path="/" element={(
        <>
          <Link to="/coaches">Koçlara git</Link>
          <button type="button" onClick={() => navigate(1)}>İleri</button>
          <PublicFooter />
        </>
      )} />
      <Route path="/coaches" element={(
        <>
          <h1>Koçlar</h1>
          <button type="button" onClick={() => navigate(-1)}>Geri</button>
        </>
      )} />
      <Route path="/kocluk" element={(
        <>
          <Link to="#kocluk-paketleri">Paketleri İncele</Link>
          <section id="kocluk-paketleri">Paketler</section>
          <PublicFooter />
        </>
      )} />
    </Routes>
  );
}

function renderFixture(initialEntry: { pathname: string; hash?: string; key: string }) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <NavigationScrollManager />
      <NavigationFixture />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  scrollY = 0;
  vi.spyOn(window, "requestAnimationFrame").mockImplementation((callback) => {
    callback(0);
    return 1;
  });
  vi.spyOn(window, "cancelAnimationFrame").mockImplementation(() => undefined);
  vi.spyOn(window, "scrollTo").mockImplementation(() => undefined);
  Object.defineProperty(Element.prototype, "scrollIntoView", {
    configurable: true,
    value: vi.fn(),
  });
  vi.spyOn(window, "scrollY", "get").mockImplementation(() => scrollY);
  vi.spyOn(window, "scrollX", "get").mockImplementation(() => 0);
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  if (originalScrollIntoView) {
    Object.defineProperty(Element.prototype, "scrollIntoView", {
      configurable: true,
      value: originalScrollIntoView,
    });
  } else {
    delete (Element.prototype as Partial<Element>).scrollIntoView;
  }
});

describe("navigation scroll behavior", () => {
  it("starts a normal route navigation at the top", () => {
    renderFixture({ pathname: "/", key: "normal-navigation-start" });
    vi.mocked(window.scrollTo).mockClear();

    fireEvent.click(screen.getByRole("link", { name: "Koçlara git" }));

    expect(screen.getByRole("heading", { name: "Koçlar" })).toBeInTheDocument();
    expect(window.scrollTo).toHaveBeenLastCalledWith({ behavior: "auto", left: 0, top: 0 });
  });

  it("scrolls cross-route and same-route package links to their target", () => {
    renderFixture({ pathname: "/", key: "cross-route-package-start" });
    const scrollIntoView = vi.mocked(Element.prototype.scrollIntoView);
    scrollIntoView.mockClear();

    fireEvent.click(screen.getByRole("link", { name: "Koçluk Paketleri" }));
    expect(screen.getByText("Paketler")).toBeInTheDocument();
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: "auto", block: "start" });

    scrollIntoView.mockClear();
    fireEvent.click(screen.getByRole("link", { name: "Koçluk Paketleri" }));
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: "auto", block: "start" });

    scrollIntoView.mockClear();
    fireEvent.click(screen.getByRole("link", { name: "Paketleri İncele" }));
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: "auto", block: "start" });
  });

  it("restores saved positions on Back and Forward navigation", () => {
    renderFixture({ pathname: "/", key: "history-navigation-start" });
    vi.mocked(window.scrollTo).mockClear();

    scrollY = 420;
    fireEvent.click(screen.getByRole("link", { name: "Koçlara git" }));
    scrollY = 180;
    fireEvent.click(screen.getByRole("button", { name: "Geri" }));
    expect(window.scrollTo).toHaveBeenLastCalledWith({ behavior: "auto", left: 0, top: 420 });

    act(() => {
      fireEvent.click(screen.getByRole("button", { name: "İleri" }));
    });
    expect(window.scrollTo).toHaveBeenLastCalledWith({ behavior: "auto", left: 0, top: 180 });
  });
});
