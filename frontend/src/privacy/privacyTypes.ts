export interface MarketingChannelPreference {
  granted: boolean;
  grantedAt: string | null;
  withdrawnAt: string | null;
}

export interface MarketingPreferences {
  email: MarketingChannelPreference;
  sms: MarketingChannelPreference;
}

export interface PrivacyPreferences {
  necessaryAllowed: boolean;
  analyticsAllowed: boolean;
  marketingAllowed: boolean;
  cookiePolicyDocumentId: number | null;
  policyVersion: string | null;
  grantedAt: string | null;
  updatedAt: string | null;
}

export interface AccountDeletionResponse {
  status: "REQUESTED" | "PROCESSING" | "COMPLETED" | "FAILED" | "CANCELLED";
  requestedAt: string;
  completedAt: string | null;
}
