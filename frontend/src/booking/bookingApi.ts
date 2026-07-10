import { httpClient } from "../api/httpClient";
import type {
  SessionCreateRequest,
  SessionResponse,
  AvailabilityResponse,
} from "./bookingTypes";

export const bookingApi = {
  book: async (request: SessionCreateRequest): Promise<SessionResponse> => {
    return httpClient.post<SessionResponse>("/api/v1/sessions", request);
  },

  listMySessions: async (): Promise<SessionResponse[]> => {
    return httpClient.get<SessionResponse[]>("/api/v1/sessions/me");
  },

  listCoachAvailability: async (coachId: number): Promise<AvailabilityResponse[]> => {
    return httpClient.get<AvailabilityResponse[]>(`/api/v1/coaches/${coachId}/availability`);
  },
};
