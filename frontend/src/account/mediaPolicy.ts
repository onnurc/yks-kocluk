export const PROFILE_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
export const PROFILE_IMAGE_MAX_LABEL = "5 MB";
export const PROFILE_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"] as const;

export const isSupportedProfileImage = (file: File): boolean =>
  PROFILE_IMAGE_TYPES.includes(file.type as (typeof PROFILE_IMAGE_TYPES)[number])
  && file.size <= PROFILE_IMAGE_MAX_BYTES;
