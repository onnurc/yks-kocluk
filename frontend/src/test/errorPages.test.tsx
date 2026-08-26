import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AppRoutes } from "../App";
import { AppErrorBoundary } from "../errors/AppErrorBoundary";
import { TestAuthProvider } from "./TestAuthProvider";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

function renderAppRoute(path: string) {
  return render(
    <TestAuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

describe("Uniform error pages", () => {
  it.each(["/student/profile", "/this-route-does-not-exist"])(
    "renders the Uniform 404 for unknown route %s",
    (path) => {
      renderAppRoute(path);

      expect(screen.getByLabelText("Hata kodu 404")).toHaveTextContent("404");
      expect(screen.getByRole("heading", { level: 1, name: "Sayfa Bulunamadı" })).toBeInTheDocument();
      expect(screen.getByText("Uniform Akademi", { selector: ".uniform-error-page__footer" })).toBeInTheDocument();
    },
  );

  it("returns home through the primary 404 action", () => {
    renderAppRoute("/this-route-does-not-exist");

    fireEvent.click(screen.getByRole("link", { name: "Ana Sayfaya Dön" }));

    expect(screen.getByRole("heading", { level: 1, name: /Hedeflerin Kadar Disiplinli/ })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Sayfa Bulunamadı" })).not.toBeInTheDocument();
  });

  it.each(["/login", "/forgot-password"])("keeps known public route %s out of the 404 fallback", (path) => {
    renderAppRoute(path);

    expect(screen.queryByLabelText("Hata kodu 404")).not.toBeInTheDocument();
  });

  it.each(["/account", "/dashboard", "/messages", "/admin"])(
    "keeps known protected route %s in the authentication flow",
    (path) => {
      renderAppRoute(path);

      expect(screen.getByRole("heading", { level: 1, name: "Tekrar Hoş Geldin!" })).toBeInTheDocument();
      expect(screen.queryByLabelText("Hata kodu 404")).not.toBeInTheDocument();
    },
  );

  it("shows the shared 500 fallback for an unexpected render crash", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);
    const BrokenView = () => {
      throw new Error("test render failure");
    };

    render(
      <AppErrorBoundary>
        <BrokenView />
      </AppErrorBoundary>,
    );

    expect(screen.getByLabelText("Hata kodu 500")).toHaveTextContent("500");
    expect(screen.getByRole("heading", { level: 1, name: "Bir Şeyler Ters Gitti" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sayfayı Yenile" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Ana Sayfaya Dön" })).toHaveAttribute("href", "/");
  });
});
