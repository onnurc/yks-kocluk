import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.hoisted(() => vi.fn());

vi.mock("../api/httpClient", () => ({ httpClient: { post } }));

import { subscriptionManagementApi } from "../subscriptionManagement/subscriptionManagementApi";

describe("subscriptionManagementApi", () => {
  beforeEach(() => post.mockReset());

  it("uses the existing owned subscription cancellation endpoint", async () => {
    post.mockResolvedValue({});
    await subscriptionManagementApi.cancelRenewal(73);
    expect(post).toHaveBeenCalledWith("/api/v1/subscriptions/73/cancel");
  });
});
