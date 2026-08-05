import { httpClient } from "../api/httpClient";
import type { AccountDeletionResponse, MarketingPreferences, PrivacyPreferences } from "./privacyTypes";

export const privacyApi = {
  getMarketingPreferences: (): Promise<MarketingPreferences> =>
    httpClient.get<MarketingPreferences>("/api/v1/privacy/marketing-preferences"),

  updateMarketingPreferences: (request: { email?: boolean; sms?: boolean }): Promise<MarketingPreferences> =>
    httpClient.patch<MarketingPreferences>("/api/v1/privacy/marketing-preferences", request),

  getPrivacyPreferences: (): Promise<PrivacyPreferences> =>
    httpClient.get<PrivacyPreferences>("/api/v1/privacy/preferences"),

  updatePrivacyPreferences: (request: {
    necessaryAllowed: true;
    analyticsAllowed: boolean;
    marketingAllowed: boolean;
    cookiePolicyDocumentId: number;
  }): Promise<PrivacyPreferences> =>
    httpClient.patch<PrivacyPreferences>("/api/v1/privacy/preferences", request),

  withdrawExplicitConsent: (): Promise<{ legalOnboardingCompleted: false; withdrawnAt: string }> =>
    httpClient.post("/api/v1/privacy/explicit-consent/withdraw"),

  getAccountDeletion: (): Promise<AccountDeletionResponse> =>
    httpClient.get<AccountDeletionResponse>("/api/v1/privacy/account-deletion"),

  deleteAccount: (): Promise<AccountDeletionResponse> =>
    httpClient.post<AccountDeletionResponse>("/api/v1/privacy/account-deletion", { confirmation: "DELETE" }),
};
