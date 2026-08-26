import { describe, expect, it } from "vitest";
import { ApiError } from "../api/ApiError";
import { mediaErrorMessage } from "../account/mediaErrors";

describe("media error mapping", () => {
  it("maps known upload, expiry, concurrency and rate-limit errors", () => {
    expect(mediaErrorMessage(new ApiError(400, "", "", "MEDIA_SIZE_EXCEEDED"), "fallback")).toContain("5 MB");
    expect(mediaErrorMessage(new ApiError(409, "", "", "MEDIA_UPLOAD_NOT_FOUND"), "fallback")).toContain("süresi dolmuş");
    expect(mediaErrorMessage(new ApiError(409, "", "", "CONCURRENT_UPDATE"), "fallback")).toContain("başka bir işlemde");
    expect(mediaErrorMessage(new ApiError(429, "", "", "MEDIA_PRESIGN_RATE_LIMIT_EXCEEDED"), "fallback")).toContain("çok fazla");
  });

  it("does not expose unknown internal errors", () => {
    expect(mediaErrorMessage(new Error("secret stack"), "Güvenli hata mesajı")).toBe("Güvenli hata mesajı");
  });
});
