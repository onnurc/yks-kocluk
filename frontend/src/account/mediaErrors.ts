import { ApiError } from "../api/ApiError";

const messages: Record<string, string> = {
  MEDIA_SIZE_EXCEEDED: "Profil fotoğrafı 5 MB’tan küçük olmalıdır.",
  MEDIA_TYPE_NOT_ALLOWED: "Bu dosya türü desteklenmiyor. JPG, PNG veya WEBP kullanın.",
  MEDIA_METADATA_MISMATCH: "Yüklenen dosya doğrulanamadı. Lütfen dosyayı yeniden seçin.",
  MEDIA_UPLOAD_NOT_FOUND: "Yükleme süresi dolmuş olabilir. Lütfen yüklemeyi yeniden başlatın.",
  MEDIA_STATUS_INVALID: "Bu yükleme artık geçerli değil. Lütfen yeniden deneyin.",
  MEDIA_PROFILE_MISSING: "Profil bilgileri değiştiği için fotoğraf bağlanamadı. Sayfayı yenileyip tekrar deneyin.",
  MEDIA_PRESIGN_RATE_LIMIT_EXCEEDED: "Kısa sürede çok fazla yükleme denemesi yapıldı. Lütfen biraz bekleyin.",
  CONCURRENT_UPDATE: "Profiliniz başka bir işlemde değişti. Sayfayı yenileyip tekrar deneyin.",
};

export const mediaErrorMessage = (error: unknown, fallback: string): string => {
  if (error instanceof ApiError && error.code && messages[error.code]) return messages[error.code];
  if (error instanceof Error && error.message === "PROFILE_IMAGE_UPLOAD_FAILED") {
    return "Fotoğraf geçici olarak yüklenemedi. Lütfen yeniden deneyin.";
  }
  return fallback;
};
