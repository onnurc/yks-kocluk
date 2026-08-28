import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  postWithCredentials: vi.fn(),
}));

vi.mock("../api/httpClient", () => ({
  httpClient: {
    postWithCredentials: mocks.postWithCredentials,
  },
}));

import { authApi } from "../auth/authApi";

describe("auth API cookie session transport", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.postWithCredentials.mockResolvedValue({ accessToken: "short-lived-access" });
  });

  it("refreshes a reloaded session using browser credentials without a JavaScript refresh token", async () => {
    await authApi.refresh();

    expect(mocks.postWithCredentials).toHaveBeenCalledWith("/api/v1/auth/refresh");
  });
});
