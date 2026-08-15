import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { CoachesPage } from "../public/CoachesPage";
import { PublicLayout } from "../public/PublicLayout";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";

vi.mock("../coaches/coachDiscoveryApi", () => ({
  coachDiscoveryApi: { listCoaches: vi.fn() },
}));

const coach = {
  id: 42,
  fullName: "Ayşe Yılmaz",
  headline: "Matematik ve çalışma planı mentörü",
  universityName: "Boğaziçi Üniversitesi",
  tracks: ["NUMERICAL"],
  rating: null,
  totalSessions: 0,
  acceptingNewStudents: true,
  profileImageUrl: null,
  introVideoUrl: null,
};

const page = { content: [coach], page: 0, size: 9, totalElements: 1, totalPages: 1, last: true };

const verbalCoach = {
  ...coach,
  id: 43,
  fullName: "Selin Demir",
  headline: "Türkçe ve edebiyat mentörü",
  tracks: ["VERBAL"],
};

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/coaches"]}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route path="coaches" element={<CoachesPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("Koçlarımız sayfası", () => {
  it("renders real API data at /coaches inside the shared public layout", async () => {
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue(page);
    renderPage();

    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.getAllByRole("contentinfo")).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: /Hayalindeki Üniversiteye Giden Yolda/ })).toBeInTheDocument();
    expect(await screen.findByText("Ayşe Yılmaz")).toBeInTheDocument();
    expect(screen.getByText(/Boğaziçi Üniversitesi/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Profili İncele/ })).toHaveAttribute("href", "/coaches/42");
    expect(coachDiscoveryApi.listCoaches).toHaveBeenCalledWith(0, 9, { sort: "newest" });

    const navLink = within(screen.getByRole("navigation", { name: "Ana menü" })).getByRole("link", { name: "Koçlarımız" });
    expect(navLink).toHaveAttribute("aria-current", "page");
  });

  it("supports backend search, track filters and sorting", async () => {
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue(page);
    renderPage();
    await screen.findByText("Ayşe Yılmaz");

    fireEvent.change(screen.getByPlaceholderText("İsim veya uzmanlık ara..."), { target: { value: "matematik" } });
    fireEvent.change(screen.getByLabelText("Sıralama"), { target: { value: "oldest" } });
    fireEvent.click(screen.getByRole("button", { name: "Sayısal" }));

    expect(await screen.findByRole("button", { name: "Sayısal", pressed: true })).toBeInTheDocument();
    expect(coachDiscoveryApi.listCoaches).toHaveBeenLastCalledWith(0, 9, {
      q: "matematik",
      track: "NUMERICAL",
      sort: "oldest",
    });
  });

  it("refreshes backend results immediately when the track select changes", async () => {
    vi.mocked(coachDiscoveryApi.listCoaches)
      .mockResolvedValueOnce(page)
      .mockResolvedValueOnce({ ...page, content: [verbalCoach] });
    renderPage();
    expect(await screen.findByText("Ayşe Yılmaz")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Alan"), { target: { value: "VERBAL" } });

    expect(await screen.findByText("Selin Demir")).toBeInTheDocument();
    expect(screen.queryByText("Ayşe Yılmaz")).not.toBeInTheDocument();
    expect(coachDiscoveryApi.listCoaches).toHaveBeenLastCalledWith(0, 9, {
      q: "",
      track: "VERBAL",
      sort: "newest",
    });
  });

  it("shows loading, empty and safe retryable error states", async () => {
    vi.mocked(coachDiscoveryApi.listCoaches).mockReturnValue(new Promise(() => undefined));
    const loading = renderPage();
    expect(screen.getByLabelText("Koçlar yükleniyor")).toHaveAttribute("aria-busy", "true");
    loading.unmount();

    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [], totalElements: 0, totalPages: 0 });
    const empty = renderPage();
    expect(await screen.findByText("Bu filtrelere uygun koç bulunamadı.")).toBeInTheDocument();
    empty.unmount();

    vi.mocked(coachDiscoveryApi.listCoaches).mockRejectedValue(new Error("özel servis hatası"));
    renderPage();
    expect(await screen.findByText("Koçlar şu anda görüntülenemiyor.")).toBeInTheDocument();
    expect(screen.queryByText("özel servis hatası")).not.toBeInTheDocument();
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue(page);
    fireEvent.click(screen.getByRole("button", { name: "Yeniden Dene" }));
    expect(await screen.findByText("Ayşe Yılmaz")).toBeInTheDocument();
  });

  it("uses an initial-based fallback instead of a fabricated coach image or score", async () => {
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue(page);
    renderPage();
    expect(await screen.findByLabelText("Ayşe Yılmaz için profil fotoğrafı bulunmuyor")).toHaveTextContent("AY");
    expect(screen.queryByText(/9\.8/)).not.toBeInTheDocument();
    expect(screen.getByText("Profil bilgilerini inceleyin")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Hemen Kayıt Ol/ })).toHaveAttribute("href", "/register");
  });
});
