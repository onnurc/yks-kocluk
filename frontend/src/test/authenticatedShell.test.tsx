import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { CurrentUser } from "../auth/authTypes";
import { AppLayout } from "../components/AppLayout";
import { TestAuthProvider } from "./TestAuthProvider";

const mocks = vi.hoisted(() => ({
  listConversations: vi.fn(),
  logout: vi.fn(),
  useNotificationSocket: vi.fn(),
}));

vi.mock("../messaging/messagingApi", () => ({
  messagingApi: { listConversations: mocks.listConversations },
}));
vi.mock("../messaging/useNotificationSocket", () => ({
  useNotificationSocket: mocks.useNotificationSocket,
}));
vi.mock("../legal/useLegalDocuments", () => ({
  useLegalDocuments: () => ({ documents: {}, loading: false, error: null, reload: vi.fn() }),
}));

const student: CurrentUser = {
  id: 9,
  email: "selin@example.com",
  fullName: "Selin Erdem",
  role: "STUDENT",
  status: "ACTIVE",
  emailVerified: true,
  legalOnboardingCompleted: true,
  hasLocalPassword: true,
};

function renderShell(path: string, user: CurrentUser = student) {
  return render(
    <TestAuthProvider value={{ user, isAuthenticated: true, logout: mocks.logout }}>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<div>Dashboard body</div>} />
            <Route path="/messages" element={<div>Messages body</div>} />
            <Route path="/messages/:conversationId" element={<div>Conversation body</div>} />
            <Route path="/admin" element={<div>Admin body</div>} />
          </Route>
          <Route path="/login" element={<div>Login body</div>} />
        </Routes>
      </MemoryRouter>
    </TestAuthProvider>,
  );
}

afterEach(cleanup);

describe("shared authenticated product shell", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.listConversations.mockResolvedValue([]);
    mocks.logout.mockResolvedValue(undefined);
  });

  it.each([
    ["/dashboard", "Dashboard body"],
    ["/messages", "Messages body"],
    ["/messages/12", "Conversation body"],
  ])("keeps the same Uniform shell at %s", (path, body) => {
    const { container } = renderShell(path);

    expect(screen.getByText(body)).toBeInTheDocument();
    expect(container.querySelector(".app-layout__header")).toBeInTheDocument();
    expect(container.querySelector(".app-layout__sidebar")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Uniform Akademi ana sayfa" })).toBeInTheDocument();
    expect(screen.getByText("Mentorluk")).toBeInTheDocument();
    expect(screen.queryByText("YKS Koçluk")).not.toBeInTheDocument();
  });

  it("renders the responsive authenticated header and opens its real mobile menu", () => {
    const { container } = renderShell("/dashboard");
    const menuButton = screen.getByRole("button", { name: "Menüyü aç" });

    expect(menuButton).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(menuButton);

    expect(screen.getByRole("button", { name: "Menüyü kapat" })).toHaveAttribute("aria-expanded", "true");
    expect(container.querySelector(".app-layout__sidebar")).toHaveClass("is-open");
    expect(screen.getByRole("button", { name: "Hesap menüsünü aç" })).toBeInTheDocument();
  });

  it("uses only real student product destinations and keeps logout in the menu", async () => {
    renderShell("/dashboard");
    const navigation = screen.getByRole("navigation", { name: "Ürün navigasyonu" });

    expect(within(navigation).getByRole("link", { name: "Panel" })).toHaveAttribute("href", "/dashboard");
    expect(within(navigation).getByRole("link", { name: "Koçlar" })).toHaveAttribute("href", "/coaches");
    expect(within(navigation).getByRole("link", { name: "Mesajlar" })).toHaveAttribute("href", "/messages");
    expect(within(navigation).getByRole("link", { name: "Görüşmeler" })).toHaveAttribute("href", "/bookings");
    expect(within(navigation).getByRole("link", { name: "Hesabım" })).toHaveAttribute("href", "/account");
    expect(within(navigation).getByRole("link", { name: "Ayarlar" })).toHaveAttribute("href", "/security");
    expect(screen.queryByText("Courses")).not.toBeInTheDocument();
    expect(screen.queryByText("Grades")).not.toBeInTheDocument();

    const logout = screen.getByRole("button", { name: "Çıkış yap" });
    expect(logout).not.toHaveStyle({ backgroundColor: "#dc3545" });
    fireEvent.click(logout);

    await waitFor(() => expect(mocks.logout).toHaveBeenCalledTimes(1));
    expect(await screen.findByText("Login body")).toBeInTheDocument();
  });

  it("keeps student-only navigation out of the admin shell", () => {
    renderShell("/admin", { ...student, role: "ADMIN", fullName: "Deniz Admin" });
    const navigation = screen.getByRole("navigation", { name: "Ürün navigasyonu" });

    expect(within(navigation).getByRole("link", { name: "Admin Paneli" })).toBeInTheDocument();
    expect(within(navigation).getByRole("link", { name: "Koç Başvuruları" })).toBeInTheDocument();
    expect(within(navigation).queryByRole("link", { name: "Koçlar" })).not.toBeInTheDocument();
    expect(within(navigation).queryByRole("link", { name: "Görüşmeler" })).not.toBeInTheDocument();
    expect(mocks.listConversations).not.toHaveBeenCalled();
  });
});
