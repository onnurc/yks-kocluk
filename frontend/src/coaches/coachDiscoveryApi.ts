import { httpClient } from "../api/httpClient";
import type { CoachSummaryResponse, CoachDetailResponse, PackageOfferResponse, PublicCoachDetailResponse } from "./coachDiscoveryTypes";

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

export interface CoachSearchParams {
  page?: number;
  size?: number;
  q?: string;
  track?: string;
  sort?: "createdAt,desc" | "createdAt,asc";
}

export const coachDiscoveryApi = {
  listCoaches: async (pageOrParams: number | CoachSearchParams = 0, size = 20, filters: CoachSearchFilters = {}): Promise<PageWrapper<CoachSummaryResponse>> => {
    const legacyParams = typeof pageOrParams === "number" ? null : pageOrParams;
    const page = legacyParams?.page ?? (typeof pageOrParams === "number" ? pageOrParams : 0);
    const requestSize = legacyParams?.size ?? size;
    const requestFilters = legacyParams ?? filters;
    const params = new URLSearchParams({
      page: String(page),
      size: String(requestSize),
      sort: legacyParams?.sort ?? `createdAt,${filters.sort === "oldest" ? "asc" : "desc"}`,
    });
    if (requestFilters.q?.trim()) params.set("q", requestFilters.q.trim());
    if (requestFilters.track) params.set("track", requestFilters.track);
    return httpClient.get<PageWrapper<CoachSummaryResponse>>(`/api/v1/public/coaches?${params.toString()}`);
  },
  getCoachDetail: async (id: number): Promise<CoachDetailResponse> => {
    return httpClient.get<CoachDetailResponse>(`/api/v1/coaches/${id}`);
  },
  getPublicCoachDetail: async (id: number): Promise<PublicCoachDetailResponse> => {
    return httpClient.get<PublicCoachDetailResponse>(`/api/v1/public/coaches/${id}`);
  },
  listCoachPackages: async (coachId: number): Promise<PackageOfferResponse[]> => {
    return httpClient.get<PackageOfferResponse[]>(`/api/v1/public/coaches/${coachId}/packages`);
  },
};
