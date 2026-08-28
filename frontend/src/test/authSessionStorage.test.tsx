import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AuthProvider, useAuth } from "../auth/AuthProvider";
import { clearAllTokens, getAccessToken, setAccessToken } from "../auth/tokenStorage";

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  logout: vi.fn(),
  getCurrentUser: vi.fn(),
  ensureFreshToken: vi.fn(),
}));

vi.mock("../auth/authApi", () => ({
  authApi: {
    login: mocks.login,
    register: vi.fn(),
    exchangeOAuthCode: vi.fn(),
    getCurrentUser: mocks.getCurrentUser,
    logout: mocks.logout,
  },
}));

vi.mock("../api/httpClient", () => ({
  ensureFreshToken: mocks.ensureFreshToken,
}));

const currentUser = {
  id: 42,
  email: "student@example.test",
  fullName: "Test Student",
  role: "STUDENT" as const,
  status: "ACTIVE" as const,
  emailVerified: true,
  legalOnboardingCompleted: true,
  hasLocalPassword: true,
};

function AuthProbe() {
  const auth = useAuth();

  return (
    <div>
      <span data-testid="loading">{String(auth.isLoading)}</span>
      <span data-testid="user-email">{auth.user?.email ?? "signed-out"}</span>
      <button type="button" onClick={() => void auth.login("student@example.test", "valid-password")}>login</button>
      <button type="button" onClick={() => void auth.logout()}>logout</button>
    </div>
  );
}

const renderProvider = () => render(
  <AuthProvider>
    <AuthProbe />
  </AuthProvider>,
);

describe("cookie-based auth storage migration", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    window.localStorage.clear();
    clearAllTokens();
    vi.clearAllMocks();
    mocks.ensureFreshToken.mockResolvedValue(null);
    mocks.logout.mockResolvedValue(undefined);
  });

  it("removes both legacy auth keys on initialization without clearing unrelated storage", async () => {
    window.localStorage.setItem("yks_coaching_access_token", "legacy-access");
    window.localStorage.setItem("yks_coaching_refresh_token", "legacy-refresh");
    window.localStorage.setItem("guestCart", "saved-cart");

    renderProvider();

    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));
    expect(window.localStorage.getItem("yks_coaching_access_token")).toBeNull();
    expect(window.localStorage.getItem("yks_coaching_refresh_token")).toBeNull();
    expect(window.localStorage.getItem("guestCart")).toBe("saved-cart");
  });

  it("defensively removes legacy auth keys during logout", async () => {
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));
    window.localStorage.setItem("yks_coaching_access_token", "legacy-access");
    window.localStorage.setItem("yks_coaching_refresh_token", "legacy-refresh");
    window.localStorage.setItem("guestCart", "saved-cart");

    fireEvent.click(screen.getByRole("button", { name: "logout" }));

    await waitFor(() => expect(mocks.logout).toHaveBeenCalledOnce());
    expect(window.localStorage.getItem("yks_coaching_access_token")).toBeNull();
    expect(window.localStorage.getItem("yks_coaching_refresh_token")).toBeNull();
    expect(window.localStorage.getItem("guestCart")).toBe("saved-cart");
  });

  it("keeps a newly issued access token in memory and never writes auth tokens to localStorage", async () => {
    mocks.login.mockResolvedValue({ accessToken: "short-lived-access" });
    mocks.getCurrentUser.mockResolvedValue(currentUser);
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("loading")).toHaveTextContent("false"));

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "login" }));
    });

    await waitFor(() => expect(screen.getByTestId("user-email")).toHaveTextContent(currentUser.email));
    expect(getAccessToken()).toBe("short-lived-access");
    expect(window.localStorage.getItem("yks_coaching_access_token")).toBeNull();
    expect(window.localStorage.getItem("yks_coaching_refresh_token")).toBeNull();
  });

  it("restores a reloaded session through the cookie refresh path without stored tokens", async () => {
    mocks.ensureFreshToken.mockImplementation(async () => {
      setAccessToken("refreshed-access");
      return "refreshed-access";
    });
    mocks.getCurrentUser.mockResolvedValue(currentUser);

    renderProvider();

    await waitFor(() => expect(screen.getByTestId("user-email")).toHaveTextContent(currentUser.email));
    expect(mocks.ensureFreshToken).toHaveBeenCalledOnce();
    expect(mocks.ensureFreshToken).toHaveBeenCalledWith();
    expect(getAccessToken()).toBe("refreshed-access");
    expect(window.localStorage.getItem("yks_coaching_access_token")).toBeNull();
    expect(window.localStorage.getItem("yks_coaching_refresh_token")).toBeNull();
  });
});
