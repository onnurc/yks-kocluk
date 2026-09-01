import { describe, expect, it } from "vitest";
import { resolveApiBaseUrl } from "../api/apiBaseUrl";

describe("resolveApiBaseUrl", () => {
  it("keeps the localhost default for development", () => {
    expect(resolveApiBaseUrl(undefined, true)).toBe("http://localhost:8080");
  });

  it("requires an explicit production URL", () => {
    expect(() => resolveApiBaseUrl(undefined, false)).toThrow(/required/);
  });

  it.each([
    "http://api.example.com",
    "https://localhost:8080",
    "https://127.0.0.1",
    "https://0.0.0.0",
    "https://[::1]",
    "https://192.168.1.5",
    "https://user:pass@api.example.com",
    "not a url",
  ])("rejects unsafe production value %s", (value) => {
    expect(() => resolveApiBaseUrl(value, false)).toThrow();
  });

  it("accepts and normalizes an explicit public HTTPS production URL", () => {
    expect(resolveApiBaseUrl(" https://api.example.com/ ", false)).toBe("https://api.example.com");
  });
});
