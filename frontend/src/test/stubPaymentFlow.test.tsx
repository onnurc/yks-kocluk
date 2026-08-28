import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { PendingPaymentWarning } from "../studentDashboard/PendingPaymentWarning";
import { StubPaymentPage } from "../subscriptionCheckout/StubPaymentPage";
import { paymentIdFromStubToken } from "../subscriptionCheckout/stubPaymentToken";
import { studentDashboardApi } from "../studentDashboard/studentDashboardApi";
import { subscriptionCheckoutApi } from "../subscriptionCheckout/subscriptionCheckoutApi";
import { ApiError } from "../api/ApiError";

vi.mock("../studentDashboard/studentDashboardApi", () => ({
  studentDashboardApi: { getDashboardData: vi.fn() },
}));
vi.mock("../subscriptionCheckout/subscriptionCheckoutApi", () => ({
  subscriptionCheckoutApi: { checkout: vi.fn(), stubSucceed: vi.fn() },
}));

const token = "stub-checkout-52-123e4567-e89b-42d3-a456-426614174000";
const student = {
  id: 71, email: "student@example.com", fullName: "Öğrenci", role: "STUDENT" as const,
  status: "ACTIVE" as const, emailVerified: true, legalOnboardingCompleted: true, hasLocalPassword: true,
};
const subscription = {
  id: 41, status: "PENDING_PAYMENT" as const, coachId: 9, coachName: "Derya Koç", packageId: 5,
  packageName: "Sınav Odaklı Paket", startAt: "2029-01-01T00:00:00Z", endAt: "2029-02-01T00:00:00Z",
  autoRenew: true, cancelledAt: null, terminationReason: null,
};
const payment = { id: 52, status: "PENDING" as const, amount: 2450, createdAt: "2029-01-01T00:00:00Z" };
const pendingDashboard = { user: student, subscription, payment };
const activeDashboard = {
  ...pendingDashboard,
  subscription: { ...subscription, status: "ACTIVE" as const },
  payment: { ...payment, status: "SUCCESS" as const },
};

function renderStubPage(path = `/payment/stub/${token}`) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/payment/stub/:token" element={<StubPaymentPage />} />
        <Route path="/dashboard" element={<p>Öğrenci paneli</p>} />
        <Route path="/coaches/:id" element={<p>Koç profili</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.stubEnv("VITE_ENABLE_STUB_PAYMENT_SUCCESS", "true");
  vi.mocked(studentDashboardApi.getDashboardData).mockResolvedValue(pendingDashboard);
  vi.mocked(subscriptionCheckoutApi.stubSucceed).mockResolvedValue({
    id: 41, coachProfileId: 9, coachName: "Derya Koç", packageId: 5, packageName: "Sınav Odaklı Paket",
    weeklySessions: 1, status: "ACTIVE", startAt: "2029-01-01T00:00:00Z", endAt: "2029-02-01T00:00:00Z",
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.unstubAllEnvs();
});

describe("local stub payment flow", () => {
  it("parses only the expected opaque local checkout token format", () => {
    expect(paymentIdFromStubToken(token)).toBe(52);
    expect(paymentIdFromStubToken("stub-checkout-52-attacker")).toBeNull();
    expect(paymentIdFromStubToken(undefined)).toBeNull();
  });

  it("renders the pending package, coach, and amount summary", async () => {
    renderStubPage();

    expect(await screen.findByRole("heading", { name: "Güvenli Ödeme Simülasyonu" })).toBeInTheDocument();
    expect(screen.getByText("Derya Koç")).toBeInTheDocument();
    expect(screen.getByText("Sınav Odaklı Paket")).toBeInTheDocument();
    expect(screen.getByText("₺2.450,00")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Test Ödemesini Başarılı Yap" })).toBeEnabled();
  });

  it("uses the backend state machine and confirms the refreshed ACTIVE/SUCCESS state", async () => {
    vi.mocked(studentDashboardApi.getDashboardData)
      .mockResolvedValueOnce(pendingDashboard)
      .mockResolvedValueOnce(activeDashboard);
    renderStubPage();

    fireEvent.click(await screen.findByRole("button", { name: "Test Ödemesini Başarılı Yap" }));

    await waitFor(() => expect(subscriptionCheckoutApi.stubSucceed).toHaveBeenCalledWith(52));
    expect(await screen.findByRole("heading", { name: "Test ödemesi başarıyla tamamlandı" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Öğrenci Paneline Git" })).toHaveAttribute("href", "/dashboard");
    expect(screen.queryByRole("button", { name: "Test Ödemesini Başarılı Yap" })).not.toBeInTheDocument();
  });

  it("rejects a token that does not match the signed-in student's pending payment", async () => {
    renderStubPage("/payment/stub/stub-checkout-999-123e4567-e89b-42d3-a456-426614174000");

    expect(await screen.findByRole("alert")).toHaveTextContent(/mevcut hesabınızla eşleşmiyor/i);
    expect(subscriptionCheckoutApi.stubSucceed).not.toHaveBeenCalled();
  });

  it("refreshes the dashboard after the fallback pending-payment action succeeds", async () => {
    const onRefresh = vi.fn();
    render(<PendingPaymentWarning payment={payment} onRefresh={onRefresh} />);

    fireEvent.click(screen.getByRole("button", { name: "Local Test: Ödemeyi Başarılı Yap" }));

    await waitFor(() => expect(subscriptionCheckoutApi.stubSucceed).toHaveBeenCalledWith(52));
    await waitFor(() => expect(onRefresh).toHaveBeenCalledTimes(1));
  });

  it("renders a safe inline error instead of an alert when local success fails", async () => {
    vi.mocked(subscriptionCheckoutApi.stubSucceed).mockRejectedValue(
      new ApiError(404, "Not Found", "Internal mapping detail", "PAYMENT_NOT_FOUND"),
    );
    const alertSpy = vi.spyOn(window, "alert").mockImplementation(() => undefined);
    render(<PendingPaymentWarning payment={payment} onRefresh={vi.fn()} />);

    fireEvent.click(screen.getByRole("button", { name: "Local Test: Ödemeyi Başarılı Yap" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Yerel test ödemesi onaylanamadı");
    expect(screen.queryByText(/Internal mapping detail/)).not.toBeInTheDocument();
    expect(alertSpy).not.toHaveBeenCalled();
  });
});
