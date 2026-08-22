import { httpClient } from "../api/httpClient";

export interface CoachApplicationRequest {
  fullName: string;
  email: string;
  phone?: string;
  experience?: string;
}

export const coachApplicationApi = {
  submit: (request: CoachApplicationRequest): Promise<void> =>
    httpClient.post<void>("/api/v1/public/coach-applications", request),
};
