import { describe, expect, it } from "vitest";
import { ApiError } from "../api/ApiError";
import {
  EMAIL_ALREADY_EXISTS_MESSAGE,
  EMAIL_INVALID_MESSAGE,
  EMAIL_REQUIRED_MESSAGE,
  emailMessageFromApiError,
  validateEmailForUx,
} from "../validation/emailValidation";

describe("email validation UX helper", () => {
  it.each(["@gmail.com", "emre@gmail", "emre..test@gmail.com", "emre@gmail..com", "plain-text"])(
    "rejects malformed email %s",
    (email) => expect(validateEmailForUx(email)).toBe(EMAIL_INVALID_MESSAGE),
  );

  it.each(["emre.test@gmail.com", "emre_123@icloud.com", "selin+test@gmail.com", "ad.soyad@ogrenci.medipol.edu.tr"])(
    "accepts well-formed email %s",
    (email) => expect(validateEmailForUx(email)).toBeNull(),
  );

  it("uses a dedicated required message for blank email", () => {
    expect(validateEmailForUx("   ")).toBe(EMAIL_REQUIRED_MESSAGE);
  });

  it("maps duplicate and validation API errors without exposing backend text", () => {
    expect(emailMessageFromApiError(new ApiError(409, "Conflict", "technical", "EMAIL_ALREADY_EXISTS")))
      .toBe(EMAIL_ALREADY_EXISTS_MESSAGE);
    expect(emailMessageFromApiError(new ApiError(400, "Bad Request", "Doğrulama hatası", undefined, [
      { field: "email", message: "E-posta zorunludur" },
    ]))).toBe(EMAIL_REQUIRED_MESSAGE);
  });
});
