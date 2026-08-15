import { httpClient } from "../api/httpClient";
import type { CoachSummaryResponse, CoachDetailResponse, PackageResponse, PublicCoachDetailResponse } from "./coachDiscoveryTypes";

export interface PageWrapper<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  page: number;
  last: boolean;
}

export interface CoachSearchFilters {
  q?: string;
  track?: string;
  sort?: "newest" | "oldest";
}

export const coachDiscoveryApi = {
  listCoaches: async (page = 0, size = 20, filters: CoachSearchFilters = {}): Promise<PageWrapper<CoachSummaryResponse>> => {
    const params = new URLSearchParams({
      page: String(page),
      size: String(size),
      sort: `createdAt,${filters.sort === "oldest" ? "asc" : "desc"}`,
    });
    if (filters.q?.trim()) params.set("q", filters.q.trim());
    if (filters.track) params.set("track", filters.track);
    return httpClient.get<PageWrapper<CoachSummaryResponse>>(`/api/v1/public/coaches?${params.toString()}`);
  },
  getCoachDetail: async (id: number): Promise<CoachDetailResponse> => {
    return httpClient.get<CoachDetailResponse>(`/api/v1/coaches/${id}`);
  },
  getPublicCoachDetail: async (id: number): Promise<PublicCoachDetailResponse> => {
    return httpClient.get<PublicCoachDetailResponse>(`/api/v1/public/coaches/${id}`);
  },
  listPackages: async (): Promise<PackageResponse[]> => {
    return httpClient.get<PackageResponse[]>("/api/v1/packages");
  },
};
