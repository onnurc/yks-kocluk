import type { CurrentUser } from "./authTypes";

export const homePathForUser = (user: CurrentUser): string =>
  user.role === "ADMIN" ? "/admin" : "/dashboard";
