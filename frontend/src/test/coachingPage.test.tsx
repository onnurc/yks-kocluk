import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { CoachingPage } from "../public/CoachingPage";
import { HomePage } from "../public/HomePage";
import { AboutPage } from "../public/AboutPage";
import { PublicLayout } from "../public/PublicLayout";
import { publicPackagesApi } from "../public/publicPackagesApi";
import { TestAuthProvider } from "./TestAuthProvider";

vi.mock("../public/publicPackagesApi", () => ({
  publicPackagesApi: { list: vi.fn() },
}));

const packageFixtures = [
  { id: 1, name: "Aylık", weeklySessions: 1, durationDays: 30, price: 2990 },
  { id: 2, name: "3 Aylık", weeklySessions: 1, durationDays: 90, price: 7990 },
  { id: 3, name: "Yıllık", weeklySessions: 1, durationDays: 365, price: 24990 },
];

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderPublicRoute(path = "/kocluk") {
  return render(
    <TestAuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<PublicLayout />}>
            <Route index element={<HomePage />} />
            <Route path="biz-kimiz" element={<AboutPage />} />
            <Route path="kocluk" element={<CoachingPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

describe("Koçluk sayfası", () => {
  it("renders at /kocluk with the shared layout and active navigation", async () => {
    vi.mocked(publicPackagesApi.list).mockResolvedValue(packageFixtures);
    renderPublicRoute();

    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.getAllByRole("contentinfo")).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: /Akademik Başarıya Giden Yolda/ })).toBeInTheDocument();
    const navLink = within(screen.getByRole("navigation", { name: "Ana menü" })).getByRole("link", { name: "Koçluk" });
    expect(navLink).toHaveAttribute("href", "/kocluk");
    expect(navLink).toHaveAttribute("aria-current", "page");
    expect(await screen.findByText("₺7.990")).toBeInTheDocument();
    expect(publicPackagesApi.list).toHaveBeenCalledTimes(1);
  });

  it("shows loading, empty and retryable error states without crashing the page", async () => {
    vi.mocked(publicPackagesApi.list).mockReturnValue(new Promise(() => undefined));
    const loadingView = renderPublicRoute();
    expect(screen.getByLabelText("Koçluk paketleri yükleniyor")).toHaveAttribute("aria-busy", "true");
    loadingView.unmount();

    vi.mocked(publicPackagesApi.list).mockResolvedValue([]);
    const emptyView = renderPublicRoute();
    expect(await screen.findByText("Aktif koçluk paketi bulunmuyor.")).toBeInTheDocument();
    emptyView.unmount();

    vi.mocked(publicPackagesApi.list).mockRejectedValue(new Error("özel backend hatası"));
    renderPublicRoute();
    expect(await screen.findByText("Paketler şu anda görüntülenemiyor.")).toBeInTheDocument();
    expect(screen.queryByText("özel backend hatası")).not.toBeInTheDocument();
    vi.mocked(publicPackagesApi.list).mockResolvedValue(packageFixtures);
    fireEvent.click(screen.getByRole("button", { name: "Yeniden Dene" }));
    expect(await screen.findByText("₺2.990")).toBeInTheDocument();
  });

  it("uses backend package values and legitimate registration CTAs", async () => {
    vi.mocked(publicPackagesApi.list).mockResolvedValue(packageFixtures);
    renderPublicRoute();

    expect(await screen.findByText("365 günlük, haftada 1 görüşme içeren mentörlük planı.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ücretsiz Görüşme Başlat" })).toHaveAttribute("href", "/register");
    expect(screen.getByRole("link", { name: "Hemen Başla" })).toHaveAttribute("href", "/register");
    expect(screen.getByRole("link", { name: "Paketleri İncele" })).toHaveAttribute("href", "#kocluk-paketleri");
    expect(screen.getByRole("button", { name: "Tanıtım videosu henüz kullanıma hazır değil" })).toBeDisabled();
  });

  it("keeps the existing home and Biz Kimiz routes functional", () => {
    vi.mocked(publicPackagesApi.list).mockResolvedValue(packageFixtures);
    const homeView = renderPublicRoute("/");
    expect(screen.getByRole("heading", { level: 1, name: /Hedeflerin Kadar Disiplinli/ })).toBeInTheDocument();
    homeView.unmount();

    renderPublicRoute("/biz-kimiz");
    expect(screen.getByRole("heading", { level: 1, name: "Geleceği İnşa Eden Bir Vizyon." })).toBeInTheDocument();
  });
});
