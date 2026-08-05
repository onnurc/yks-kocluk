import { ApiError } from "../api/ApiError";

const messages: Record<string, string> = {
  REQUIRED_LEGAL_ACCEPTANCE_MISSING: "Kayıt için gerekli hukuki metinleri onaylamalısınız.",
  LEGAL_DOCUMENT_NOT_CURRENT: "Hukuki metinlerin yeni bir sürümü yayımlandı. Lütfen tekrar kontrol edin.",
  LEGAL_DOCUMENT_NOT_FOUND: "Hukuki metinler yüklenemedi. Lütfen yeniden deneyin.",
  REQUIRED_CHECKOUT_LEGAL_ACCEPTANCE_MISSING: "Ödeme öncesinde sözleşme ve bilgilendirme metinlerini kabul etmelisiniz.",
  CHECKOUT_LEGAL_DOCUMENT_NOT_CURRENT: "Hukuki metinlerin yeni bir sürümü yayımlandı. Lütfen yeniden inceleyin.",
  CHECKOUT_LEGAL_DOCUMENT_TYPE_MISMATCH: "Ödeme için seçilen hukuki metinler geçerli değil. Lütfen yeniden inceleyin.",
  LEGAL_ONBOARDING_REQUIRED: "Devam etmek için hukuki onayınızı tamamlayın.",
};

export const legalErrorMessage = (error: unknown): string | null => {
  if (!(error instanceof ApiError) || !error.code) return null;
  return messages[error.code] || null;
};

export const isStaleLegalDocumentError = (error: unknown): boolean =>
  error instanceof ApiError && [
    "LEGAL_DOCUMENT_NOT_CURRENT",
    "CHECKOUT_LEGAL_DOCUMENT_NOT_CURRENT",
    "CHECKOUT_LEGAL_DOCUMENT_TYPE_MISMATCH",
    "LEGAL_DOCUMENT_NOT_FOUND",
  ].includes(error.code || "");
