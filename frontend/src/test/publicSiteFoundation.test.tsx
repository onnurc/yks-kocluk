import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PublicHeader } from "../public/PublicHeader";
import { PublicLayout } from "../public/PublicLayout";

afterEach(cleanup);

describe("public site foundation", () => {
  it("renders the shared header, outlet, and footer", () => {
    render(
      <MemoryRouter>
        <Routes>
          <Route element={<PublicLayout />}>
            <Route index element={<h1>Geçici sayfa içeriği</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    );

    expect(screen.getByRole("banner")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Geçici sayfa içeriği" })).toBeInTheDocument();
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ana içeriğe geç" })).toHaveAttribute("href", "#public-main");
  });

  it("opens and closes the keyboard-accessible mobile menu", () => {
    render(
      <MemoryRouter>
        <PublicHeader />
      </MemoryRouter>,
    );

    const menuButton = screen.getByRole("button", { name: "Menüyü aç" });
    fireEvent.click(menuButton);

    const menu = screen.getByRole("dialog", { name: "Mobil menü" });
    expect(menuButton).toHaveAttribute("aria-expanded", "true");
    expect(menu).toBeInTheDocument();
    expect(screen.getAllByText("Biz Kimiz").length).toBeGreaterThan(0);

    fireEvent.keyDown(menu, { key: "Escape" });

    expect(screen.queryByRole("dialog", { name: "Mobil menü" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Menüyü aç" })).toHaveFocus();
  });

  it("closes the mobile menu after following an available route", () => {
    render(
      <MemoryRouter>
        <PublicHeader />
      </MemoryRouter>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Menüyü aç" }));
    const menu = screen.getByRole("dialog", { name: "Mobil menü" });
    fireEvent.click(menu.querySelector('a[href="/login"]') as HTMLAnchorElement);

    expect(screen.queryByRole("dialog", { name: "Mobil menü" })).not.toBeInTheDocument();
  });
});
