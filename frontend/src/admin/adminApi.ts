import { httpClient } from "../api/httpClient";
import type { AdminCoach, AdminCoachCreateRequest, AdminCoachCreateResponse, AdminDashboardSummary, AdminSession, AdminUser, AdminUserDetail, CoachFilter, CoachStudent, PageResponse, SuspendResponse, UserStatus } from "./adminTypes";
import type { TrialConsultationResponse } from "../trial/trialConsultationTypes";

const query = (values: Record<string, string | number | undefined | null>) => {
  const params = new URLSearchParams();
  Object.entries(values).forEach(([key, value]) => { if (value !== undefined && value !== null && value !== "") params.set(key, String(value)); });
  return params.toString();
};

export const adminApi = {
  summary: () => httpClient.get<AdminDashboardSummary>("/api/v1/admin/dashboard/summary"),
  users: (role: "STUDENT"|"COACH", search = "", status?: UserStatus, page = 0) => httpClient.get<PageResponse<AdminUser>>(`/api/v1/admin/users?${query({ role, search, status, page, size: 20, sort: "createdAt,desc" })}`),
  user: (id:number) => httpClient.get<AdminUserDetail>(`/api/v1/admin/users/${id}`),
  coaches: (status: CoachFilter, search = "", page = 0) => httpClient.get<PageResponse<AdminCoach>>(`/api/v1/admin/coaches?${query({ status, search, page, size: 20, sort: "createdAt,desc" })}`),
  createCoach: (request: AdminCoachCreateRequest) => httpClient.post<AdminCoachCreateResponse>("/api/v1/admin/coaches", request),
  coach: (id:number) => httpClient.get<AdminCoach>(`/api/v1/admin/coaches/${id}`),
  approveCoachProfile: (id:number) => httpClient.post(`/api/v1/admin/coaches/${id}/approve`),
  rejectCoachProfile: (id:number, reason:string) => httpClient.post(`/api/v1/admin/coaches/${id}/reject`, { reason }),
  coachStudents: (id:number) => httpClient.get<PageResponse<CoachStudent>>(`/api/v1/admin/coaches/${id}/students?page=0&size=20&sort=createdAt,desc`),
  suspendUser: (id:number, reason:string) => httpClient.post<SuspendResponse>(`/api/v1/admin/users/${id}/suspend`, { reason }),
  activateUser: (id:number) => httpClient.post<SuspendResponse>(`/api/v1/admin/users/${id}/unsuspend`),
  sessions: (filters: { type?:string; status?:string; coachId?:number; studentId?:number; from?:string; to?:string; page?:number; size?:number } = {}) => httpClient.get<PageResponse<AdminSession>>(`/api/v1/admin/sessions?${query({ type: filters.type ?? "ALL", status: filters.status, coachId: filters.coachId, studentId: filters.studentId, from: filters.from, to: filters.to, page: filters.page ?? 0, size: filters.size ?? 20, sort: "createdAt,desc" })}`),
  removeProfileImage: (assetId:number, reason:string) => httpClient.post<void>(`/api/v1/admin/media/${assetId}/remove-profile-image`, { reason }),
  confirmTrial: (id:number, meetingUrl:string) => httpClient.post<TrialConsultationResponse>(`/api/v1/admin/trial-consultations/${id}/confirm`, { meetingUrl }),
};
