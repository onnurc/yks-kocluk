import { httpClient } from "../api/httpClient";
import type { AvailabilityResponse } from "../booking/bookingTypes";
import type { TrialConsultationCreateRequest, TrialConsultationResponse } from "./trialConsultationTypes";

/** Backend note: /trial-availability returns the same open-slot set as /availability —
 * any open slot can be used for either a trial or a paid session. */
export const trialConsultationApi = {
  listCoachTrialAvailability: async (coachId: number): Promise<AvailabilityResponse[]> => {
    return httpClient.get<AvailabilityResponse[]>(`/api/v1/coaches/${coachId}/trial-availability`);
  },
  request: async (body: TrialConsultationCreateRequest): Promise<TrialConsultationResponse> => {
    return httpClient.post<TrialConsultationResponse>("/api/v1/trial-consultations", body);
  },
  myTrials: async (): Promise<TrialConsultationResponse[]> => {
    return httpClient.get<TrialConsultationResponse[]>("/api/v1/trial-consultations/me");
  },
  cancel: async (id: number): Promise<TrialConsultationResponse> => {
    return httpClient.post<TrialConsultationResponse>(`/api/v1/trial-consultations/${id}/cancel`, {});
  },
};
