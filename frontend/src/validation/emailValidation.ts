import { ApiError } from "../api/ApiError";

export const EMAIL_REQUIRED_MESSAGE = "E-posta adresi zorunludur.";
export const EMAIL_INVALID_MESSAGE = "Geçerli bir e-posta adresi girin. Örnek: adiniz@gmail.com";
export const EMAIL_ALREADY_EXISTS_MESSAGE = "Bu e-posta adresiyle kayıtlı bir hesap bulunuyor.";
export const GENERIC_OPERATION_ERROR = "İşlem sırasında bir hata oluştu. Lütfen tekrar deneyin.";

// This is intentionally a lightweight client-side UX check. The backend StrictEmail
// constraint remains authoritative and may reject addresses accepted here.
export const validateEmailForUx = (value: string): string | null => {
  const email = value.trim();
  if (!email) return EMAIL_REQUIRED_MESSAGE;

  const at = email.indexOf("@");
  if (at <= 0 || at !== email.lastIndexOf("@")) return EMAIL_INVALID_MESSAGE;

  const local = email.slice(0, at);
  const domain = email.slice(at + 1);
  if (
    local.startsWith(".") || local.endsWith(".") || local.includes("..") ||
    domain.startsWith(".") || domain.endsWith(".") || domain.includes("..") ||
    !/^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+$/.test(local) ||
    !/^(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$/.test(domain)
  ) {
    return EMAIL_INVALID_MESSAGE;
  }
  return null;
};

export const emailMessageFromApiError = (error: unknown): string | null => {
  if (!(error instanceof ApiError)) return null;
  if (error.code === "EMAIL_ALREADY_EXISTS") return EMAIL_ALREADY_EXISTS_MESSAGE;
  if (error.code === "INVALID_EMAIL") return EMAIL_INVALID_MESSAGE;

  const emailFieldErrors = error.fieldErrors?.filter(({ field }) =>
    field === "email" || field.endsWith(".email")
  ) ?? [];
  if (emailFieldErrors.some(({ message }) => /zorunlu|boş|blank/i.test(message))) {
    return EMAIL_REQUIRED_MESSAGE;
  }
  return emailFieldErrors.length > 0 ? EMAIL_INVALID_MESSAGE : null;
};
