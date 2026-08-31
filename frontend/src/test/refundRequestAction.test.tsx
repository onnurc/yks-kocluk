import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RefundRequestAction } from "../refunds/RefundRequestAction";

const mocks = vi.hoisted(() => ({ eligibility: vi.fn(), create: vi.fn() }));

vi.mock("../refunds/refundRequestApi", () => ({
  refundRequestApi: { eligibility: mocks.eligibility, create: mocks.create },
}));

const response = (overrides: Record<string, unknown> = {}) => ({
  subscriptionId: 41,
  eligible: true,
  status: "ELIGIBLE",
  refundableAmount: 2450,
  currency: "TRY",
  deadline: "2026-09-05T10:00:00Z",
  explanation: "Ödeme tarihinden itibaren ilk 7 gün içinde koşulsuz iade talebi oluşturabilirsiniz.",
  activeRequestStatus: null,
  ...overrides,
});

beforeEach(() => vi.clearAllMocks());
afterEach(cleanup);

describe("student refund eligibility presentation", () => {
  it("enables the request action only when backend says eligible", async () => {
    mocks.eligibility.mockResolvedValue(response());
    render(<RefundRequestAction subscriptionId={41} onSuccess={vi.fn()} />);
    expect(await screen.findByRole("button", { name: "İade Al" })).toBeEnabled();
  });

  it("disables an expired request and renders the backend expiry explanation", async () => {
    const explanation = "İade süresi doldu. Ödeme tarihinden itibaren ilk 7 gün içinde iade talebi oluşturabilirsiniz.";
    mocks.eligibility.mockResolvedValue(response({ eligible: false, status: "WINDOW_EXPIRED", explanation }));
    render(<RefundRequestAction subscriptionId={41} onSuccess={vi.fn()} />);
    expect(await screen.findByText(explanation)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "İade Al" })).toBeDisabled();
  });

  it("disables the action while an existing refund request is active", async () => {
    mocks.eligibility.mockResolvedValue(response({
      eligible: false, status: "ACTIVE_REQUEST_EXISTS", activeRequestStatus: "PENDING",
      explanation: "Bu ödeme için işleme alınmış bir iade talebiniz var.",
    }));
    render(<RefundRequestAction subscriptionId={41} onSuccess={vi.fn()} />);
    expect(await screen.findByText("Bu ödeme için işleme alınmış bir iade talebiniz var.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "İade Al" })).toBeDisabled();
  });

  it("disables the action when no refundable balance remains", async () => {
    mocks.eligibility.mockResolvedValue(response({
      eligible: false, status: "NO_REFUNDABLE_BALANCE", refundableAmount: 0,
      explanation: "Bu ödeme için iade edilebilir bakiye kalmadı.",
    }));
    render(<RefundRequestAction subscriptionId={41} onSuccess={vi.fn()} />);
    expect(await screen.findByText("Bu ödeme için iade edilebilir bakiye kalmadı.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "İade Al" })).toBeDisabled();
  });

  it("does not override backend eligibility based on client-side session usage", async () => {
    mocks.eligibility.mockResolvedValue(response({ sessionsUsed: 5 }));
    render(<RefundRequestAction subscriptionId={41} onSuccess={vi.fn()} />);
    expect(await screen.findByRole("button", { name: "İade Al" })).toBeEnabled();
  });

  it("notifies subscription consumers after a successful refund termination", async () => {
    mocks.eligibility.mockResolvedValue(response());
    mocks.create.mockResolvedValue({ id: 9, status: "REFUNDED" });
    const onSuccess = vi.fn();
    const listener = vi.fn();
    window.addEventListener("uniform:subscription-state-changed", listener);
    render(<RefundRequestAction subscriptionId={41} onSuccess={onSuccess} />);

    fireEvent.click(await screen.findByRole("button", { name: "İade Al" }));
    fireEvent.click(within(screen.getByRole("dialog", { name: "İadeyi doğrula" })).getByRole("button", { name: "İadeyi Onayla" }));

    await waitFor(() => expect(listener).toHaveBeenCalledOnce());
    expect(onSuccess).toHaveBeenCalledOnce();
    window.removeEventListener("uniform:subscription-state-changed", listener);
  });
});
