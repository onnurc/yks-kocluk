import { httpClient } from "../api/httpClient";
import type { CoachSummaryResponse, CoachDetailResponse, PackageResponse } from "./coachDiscoveryTypes";

export interface PageWrapper<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

export const coachDiscoveryApi = {
  listCoaches: async (page = 0, size = 20): Promise<PageWrapper<CoachSummaryResponse>> => {
    return httpClient.get<PageWrapper<CoachSummaryResponse>>(`/api/v1/coaches?page=${page}&size=${size}`);
  },
  getCoachDetail: async (id: number): Promise<CoachDetailResponse> => {
    return httpClient.get<CoachDetailResponse>(`/api/v1/coaches/${id}`);
  },
  listPackages: async (): Promise<PackageResponse[]> => {
    return httpClient.get<PackageResponse[]>("/api/v1/packages");
  },
};
