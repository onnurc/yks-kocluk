import type { CurrentUser } from "./authTypes";

export const homePathForUser = (user: CurrentUser): string =>
  user.role === "ADMIN" ? "/admin" : "/dashboard";

export const readinessPathForUser = (user: CurrentUser): string => {
  if (!user.emailVerified) return "/verify-email";
  if (!user.legalOnboardingCompleted) return "/legal-onboarding";
  return homePathForUser(user);
};
