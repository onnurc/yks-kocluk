import { cleanup, render, screen, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, describe, expect, it } from "vitest";
import { HomePage } from "../public/HomePage";
import { AboutPage } from "../public/AboutPage";
import { PublicLayout } from "../public/PublicLayout";

afterEach(cleanup);

function renderPublicRoute(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route index element={<HomePage />} />
          <Route path="biz-kimiz" element={<AboutPage />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

describe("Ana Sayfa", () => {
  it("renders the real home page inside the shared public layout", () => {
    renderPublicRoute();

    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.getAllByRole("contentinfo")).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: /Hedeflerin Kadar Disiplinli/ })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Neden Uniform?" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Sıkça Sorulan Sorular" })).toBeInTheDocument();
  });

  it("connects meaningful calls to action to existing routes", () => {
    renderPublicRoute();

    expect(screen.getByRole("link", { name: "Ücretsiz Görüşme Ayarla" })).toHaveAttribute("href", "/register");
    expect(screen.getAllByRole("link", { name: "Bilgi Al" })).toHaveLength(3);
    expect(within(screen.getByRole("navigation", { name: "Ana menü" })).getByRole("link", { name: "Biz Kimiz" })).toHaveAttribute("href", "/biz-kimiz");
  });

  it("uses safe states for unavailable public data and media", () => {
    renderPublicRoute();

    expect(screen.getAllByText("Güncel fiyat için kayıt olun")).toHaveLength(3);
    expect(screen.getAllByText("Başarı hikâyesi yakında")).toHaveLength(3);
    expect(screen.getByRole("button", { name: "Tanıtım videosu henüz kullanıma hazır değil" })).toBeDisabled();
    expect(screen.queryByText("2.990 TL")).not.toBeInTheDocument();
  });

  it("keeps the Biz Kimiz public route functional", () => {
    renderPublicRoute("/biz-kimiz");
    expect(screen.getByRole("heading", { level: 1, name: "Geleceği İnşa Eden Bir Vizyon." })).toBeInTheDocument();
  });
});
