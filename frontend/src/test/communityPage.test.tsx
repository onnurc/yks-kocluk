import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { AppRoutes } from "../App";
import { TestAuthProvider } from "./TestAuthProvider";

afterEach(cleanup);

function renderCommunity() {
  return render(
    <TestAuthProvider>
      <MemoryRouter initialEntries={["/community"]}>
        <AppRoutes />
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

describe("Community page", () => {
  it("renders at /community inside the shared public header and footer", () => {
    renderCommunity();

    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.getAllByRole("contentinfo")).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: /YKS’ye hazırlanırken/ })).toBeInTheDocument();
    const navLink = within(screen.getByRole("navigation", { name: "Ana menü" })).getByRole("link", { name: "Community" });
    expect(navLink).toHaveAttribute("href", "/community");
    expect(navLink).toHaveAttribute("aria-current", "page");
  });

  it("renders the requested Community sections", () => {
    renderCommunity();

    expect(screen.getByRole("heading", { name: /üniversite sadece kazanılacak bir hedef değil/ })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Community Deneyimleri" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Süreç Deneyimi + Somut Sonuç" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Seviyeler ve Rozetler" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Yaklaşan Deneyimler ve Etkinlikler" })).toBeInTheDocument();
  });

  it("states the confirmed Give Back rule without adding eligibility conditions", () => {
    renderCommunity();

    const giveBack = screen.getByRole("heading", { name: /İlk 5.000’e gir/ }).closest("section");
    expect(giveBack).not.toBeNull();
    expect(within(giveBack as HTMLElement).getAllByText(/Sınava Kadar/).length).toBeGreaterThan(0);
    expect(within(giveBack as HTMLElement).getAllByText(/ilk 5.000/i).length).toBeGreaterThan(0);
    expect(within(giveBack as HTMLElement).getAllByText(/12 aylık/i).length).toBeGreaterThan(0);
    expect(within(giveBack as HTMLElement).getByText(/normal paket iptal ve iade politikasından ayrıdır/i)).toBeInTheDocument();
  });

  it("connects real CTAs and keeps unavailable features informational", () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch");
    renderCommunity();

    expect(screen.getByRole("link", { name: "Sınava Kadar Paketini İncele" })).toHaveAttribute("href", "/kocluk");
    expect(screen.getByRole("link", { name: "Uniform ile Hazırlanmaya Başla" })).toHaveAttribute("href", "/kocluk");
    expect(screen.getByRole("link", { name: "Koçları Keşfet" })).toHaveAttribute("href", "/coaches");
    expect(screen.getAllByRole("button", { name: /kayıt henüz açılmadı/i })).toHaveLength(3);
    screen.getAllByRole("button", { name: /kayıt henüz açılmadı/i }).forEach((button) => expect(button).toBeDisabled());
    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });
});
