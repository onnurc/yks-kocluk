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
  { id: 1, packageType: "ONE_MONTH" as const, name: "1 Aylık", purchasable: true, durationMonths: 1, untilExamMonthsRemaining: null, listPrice: 2990, effectivePrice: 2990, campaignActive: false, campaignTitle: null, campaignDescription: null, evaluationMeetingsPerMonth: 1, weeklyMeetingsPerMonth: 4, totalMeetingsPerMonth: 5 },
  { id: 2, packageType: "THREE_MONTHS" as const, name: "3 Aylık", purchasable: true, durationMonths: 3, untilExamMonthsRemaining: null, listPrice: 7990, effectivePrice: 7990, campaignActive: false, campaignTitle: null, campaignDescription: null, evaluationMeetingsPerMonth: 1, weeklyMeetingsPerMonth: 4, totalMeetingsPerMonth: 5 },
  { id: 3, packageType: "UNTIL_EXAM" as const, name: "Sınava Kadar", purchasable: true, durationMonths: null, untilExamMonthsRemaining: 9, listPrice: 24990, effectivePrice: 24990, campaignActive: false, campaignTitle: null, campaignDescription: null, evaluationMeetingsPerMonth: 1, weeklyMeetingsPerMonth: 4, totalMeetingsPerMonth: 5 },
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

    expect(await screen.findByText("Sınava kalan 9 aylık mentörlük planı.")).toBeInTheDocument();
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
