import { describe, expect, it } from "vitest";
import { blocksNewCoachCheckout, isLiveCoachRelationship } from "../access/subscriptionAccess";

describe("subscription relationship access", () => {
  it.each(["ACTIVE", "PAST_DUE"] as const)("treats %s as live and checkout-blocking", (status) => {
    expect(isLiveCoachRelationship(status)).toBe(true);
    expect(blocksNewCoachCheckout(status)).toBe(true);
  });

  it("keeps pending payment checkout-blocking without treating it as an active coaching relationship", () => {
    expect(isLiveCoachRelationship("PENDING_PAYMENT")).toBe(false);
    expect(blocksNewCoachCheckout("PENDING_PAYMENT")).toBe(true);
  });

  it.each(["TERMINATED", "EXPIRED"] as const)("releases trial and checkout eligibility for %s", (status) => {
    expect(isLiveCoachRelationship(status)).toBe(false);
    expect(blocksNewCoachCheckout(status)).toBe(false);
  });
});
