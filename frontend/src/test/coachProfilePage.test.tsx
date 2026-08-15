import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ApiError } from "../api/ApiError";
import { coachDiscoveryApi } from "../coaches/coachDiscoveryApi";
import { CoachProfilePage } from "../public/CoachProfilePage";
import { CoachesPage } from "../public/CoachesPage";
import { PublicLayout } from "../public/PublicLayout";
import { TestAuthProvider } from "./TestAuthProvider";

vi.mock("../coaches/coachDiscoveryApi", () => ({
  coachDiscoveryApi: { getPublicCoachDetail: vi.fn(), listCoaches: vi.fn() },
}));

const detail = {
  id: 42,
  fullName: "Ayşe Yılmaz",
  headline: "Matematik ve çalışma planı mentörü",
  bio: "Öğrencilerin planlı ve sürdürülebilir çalışmasına destek olur.",
  universityName: "Boğaziçi Üniversitesi",
  department: "Matematik",
  graduationYear: 2025,
  tracks: ["NUMERICAL"],
  rating: null,
  totalSessions: 18,
  acceptingNewStudents: true,
  profileImageUrl: null,
  introVideoUrl: null,
};

const similar = {
  id: 77,
  fullName: "Mert Kaya",
  headline: "Sayısal mentörü",
  universityName: "ODTÜ",
  tracks: ["NUMERICAL"],
  rating: null,
  totalSessions: 2,
  acceptingNewStudents: true,
  profileImageUrl: null,
  introVideoUrl: null,
};

const page = { content: [detail, similar], page: 0, size: 4, totalElements: 2, totalPages: 1, last: true };

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

function renderRoute(path = "/coaches/42") {
  return render(
    <TestAuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<PublicLayout />}>
            <Route path="coaches" element={<CoachesPage />} />
            <Route path="coaches/:id" element={<CoachProfilePage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

describe("Public koç profili", () => {
  it("renders real detail data by route id inside the shared layout", async () => {
    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockResolvedValue(detail);
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue(page);
    renderRoute();

    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.getAllByRole("contentinfo")).toHaveLength(1);
    expect(await screen.findByRole("heading", { level: 1, name: "Ayşe Yılmaz" })).toBeInTheDocument();
    expect(screen.getByText(/Boğaziçi Üniversitesi/)).toBeInTheDocument();
    expect(screen.getByText("18")).toBeInTheDocument();
    expect(screen.getByText("Öğrencilerin planlı ve sürdürülebilir çalışmasına destek olur.")).toBeInTheDocument();
    expect(coachDiscoveryApi.getPublicCoachDetail).toHaveBeenCalledWith(42);
    expect(coachDiscoveryApi.listCoaches).toHaveBeenCalledWith(0, 4, { track: "NUMERICAL", sort: "newest" });
    expect(screen.getByRole("link", { name: "Mert Kaya profilini incele" })).toHaveAttribute("href", "/coaches/77");
  });

  it("uses fallback media safely and routes trial CTA through registration", async () => {
    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockResolvedValue(detail);
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [] });
    renderRoute();

    expect(await screen.findByLabelText("Ayşe Yılmaz için profil fotoğrafı bulunmuyor")).toHaveTextContent("AY");
    expect(screen.getByText("Tanıtım videosu henüz eklenmedi.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Ayşe Yılmaz tanıtım videosu")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ücretsiz Görüşme İçin Kayıt Ol" })).toHaveAttribute("href", "/register?coachId=42");
    expect(screen.queryByText(/9\.8|500\+|Efe Ali/)).not.toBeInTheDocument();
  });

  it("renders real profile image and video URLs when supplied", async () => {
    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockResolvedValue({
      ...detail,
      profileImageUrl: "https://media.example/profile.jpg",
      introVideoUrl: "https://media.example/intro.mp4",
    });
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [] });
    renderRoute();

    expect(await screen.findByRole("img", { name: "Ayşe Yılmaz profil fotoğrafı" })).toHaveAttribute("src", "https://media.example/profile.jpg");
    const video = screen.getByLabelText("Ayşe Yılmaz tanıtım videosu");
    expect(video).toHaveAttribute("poster", "https://media.example/profile.jpg");
    expect(video.querySelector("source")).toHaveAttribute("src", "https://media.example/intro.mp4");
  });

  it("shows generic not-found and retryable service errors", async () => {
    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockRejectedValue(new ApiError(404, "Not found", "özel durum"));
    const missing = renderRoute();
    expect(await screen.findByText("Koç profili bulunamadı.")).toBeInTheDocument();
    expect(screen.queryByText("özel durum")).not.toBeInTheDocument();
    missing.unmount();

    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockRejectedValue(new Error("özel servis hatası"));
    renderRoute();
    expect(await screen.findByText("Profil şu anda görüntülenemiyor.")).toBeInTheDocument();
    vi.mocked(coachDiscoveryApi.getPublicCoachDetail).mockResolvedValue(detail);
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [] });
    fireEvent.click(screen.getByRole("button", { name: "Yeniden Dene" }));
    expect(await screen.findByRole("heading", { level: 1, name: "Ayşe Yılmaz" })).toBeInTheDocument();
  });

  it("keeps the public coaches catalogue route functional", async () => {
    vi.mocked(coachDiscoveryApi.listCoaches).mockResolvedValue({ ...page, content: [similar], size: 9 });
    renderRoute("/coaches");
    expect(await screen.findByRole("heading", { level: 2, name: "Mert Kaya" })).toBeInTheDocument();
  });
});
