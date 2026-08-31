import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CoachApplicationPage } from "../public/CoachApplicationPage";
import { ApiError } from "../api/ApiError";

const mocks = vi.hoisted(() => ({ submit: vi.fn() }));

vi.mock("../public/coachApplicationApi", () => ({
  coachApplicationApi: { submit: mocks.submit },
}));

beforeEach(() => vi.clearAllMocks());
afterEach(cleanup);

describe("coach application email UX", () => {
  const fill = (email: string) => {
    fireEvent.change(screen.getByLabelText("Ad Soyad:"), { target: { value: "Ada Koç" } });
    fireEvent.change(screen.getByLabelText("E-posta:"), { target: { value: email } });
  };

  it("shows malformed email under the field without native form validation", () => {
    render(<CoachApplicationPage />);
    fill("emre@gmail..com");
    fireEvent.click(screen.getByRole("button", { name: "Başvuruyu Gönder" }));

    expect(screen.getByText("Geçerli bir e-posta adresi girin. Örnek: adiniz@gmail.com", { selector: ".coach-application-field__error" })).toBeInTheDocument();
    expect(screen.getByLabelText("E-posta:")).toHaveAttribute("aria-invalid", "true");
    expect(screen.getByLabelText("E-posta:").closest("form")).toHaveAttribute("novalidate");
    expect(mocks.submit).not.toHaveBeenCalled();
  });

  it("maps backend email validation to the same field message", async () => {
    mocks.submit.mockRejectedValue(new ApiError(400, "Bad Request", "Doğrulama hatası", "INVALID_EMAIL"));
    render(<CoachApplicationPage />);
    fill("ada@example.com");
    fireEvent.click(screen.getByRole("button", { name: "Başvuruyu Gönder" }));

    expect(await screen.findByText("Geçerli bir e-posta adresi girin. Örnek: adiniz@gmail.com")).toBeInTheDocument();
    expect(screen.queryByText(/Bad Request|Doğrulama hatası/i)).not.toBeInTheDocument();
    expect(screen.getByLabelText("E-posta:")).toHaveFocus();
  });

  it("submits a valid email normally", async () => {
    mocks.submit.mockResolvedValue(undefined);
    render(<CoachApplicationPage />);
    fill("ada@example.com");
    fireEvent.click(screen.getByRole("button", { name: "Başvuruyu Gönder" }));

    await waitFor(() => expect(mocks.submit).toHaveBeenCalledWith(expect.objectContaining({ email: "ada@example.com" })));
    expect(await screen.findByRole("heading", { name: "Başvurunuz alındı" })).toBeInTheDocument();
    expect(screen.getByText("Başvuru talebiniz alındı. Başvurunuzun durumuyla ilgili gerekli bilgilendirme e-posta adresiniz üzerinden yapılacaktır.")).toBeInTheDocument();
  });
});
