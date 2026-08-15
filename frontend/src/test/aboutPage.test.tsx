import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AboutPage } from "../public/AboutPage";
import { PublicLayout } from "../public/PublicLayout";
import { TestAuthProvider } from "./TestAuthProvider";

afterEach(cleanup);

function renderAboutPage() {
  return render(
    <TestAuthProvider>
      <MemoryRouter initialEntries={["/biz-kimiz"]}>
        <Routes>
          <Route element={<PublicLayout />}>
            <Route path="biz-kimiz" element={<AboutPage />} />
          </Route>
        </Routes>
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

describe("Biz Kimiz sayfası", () => {
  it("renders at /biz-kimiz inside the shared public layout", () => {
    renderAboutPage();

    expect(screen.getAllByRole("banner")).toHaveLength(1);
    expect(screen.getAllByRole("contentinfo")).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: "Geleceği İnşa Eden Bir Vizyon." })).toBeInTheDocument();
    expect(screen.getByText(/Bir Hayalden,/)).toBeInTheDocument();
    expect(
      screen.getByRole("heading", {
        name: /Sınavın Ötesinde,\s*Birlikte Büyüyen Bir Topluluk\./,
      }),
    ).toBeInTheDocument();
  });

  it("connects the shared navigation to the implemented route", () => {
    renderAboutPage();

    const desktopNavigation = screen.getByRole("navigation", { name: "Ana menü" });
    expect(within(desktopNavigation).getByRole("link", { name: "Biz Kimiz" })).toHaveAttribute("href", "/biz-kimiz");
  });

  it("uses meaningful local imagery and exposes an honest video state", () => {
    renderAboutPage();

    expect(screen.getByAltText("Modern bir üniversite kütüphanesinde birlikte çalışan öğrenciler")).toHaveAttribute(
      "src",
      "/images/about/hero-library.jpg",
    );
    expect(screen.getByRole("button", { name: "Tanıtım videosu henüz kullanıma hazır değil" })).toBeDisabled();
    expect(screen.getAllByRole("article")).toHaveLength(3);
  });
});
