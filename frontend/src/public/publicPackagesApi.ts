import { httpClient } from "../api/httpClient";

export interface PublicPackage {
  id: number;
  packageType: "ONE_MONTH" | "THREE_MONTHS" | "UNTIL_EXAM";
  name: string;
  purchasable: boolean;
  durationMonths: number | null;
  untilExamMonthsRemaining: number | null;
  listPrice: number | null;
  effectivePrice: number | null;
  campaignActive: boolean;
  campaignTitle: string | null;
  campaignDescription: string | null;
  evaluationMeetingsPerMonth: number;
  weeklyMeetingsPerMonth: number;
  totalMeetingsPerMonth: number;
}

export const publicPackagesApi = {
  list: (): Promise<PublicPackage[]> =>
    httpClient.get<PublicPackage[]>("/api/v1/public/packages"),
};
