import { httpClient } from "../api/httpClient";
import type {
  CoachProfileResponse,
  CoachEducationUpdateRequest,
  MediaAssetResponse,
  MediaPresignResponse,
  StudentProfileResponse,
  StudentProfileUpdateRequest,
} from "./accountTypes";

export const accountApi = {
  getStudentProfile: () => httpClient.get<StudentProfileResponse>("/api/v1/student/profile/me"),
  updateStudentProfile: (request: StudentProfileUpdateRequest) =>
    httpClient.put<StudentProfileResponse>("/api/v1/student/profile/me", request),
  getCoachProfile: () => httpClient.get<CoachProfileResponse>("/api/v1/coach/profile/me"),
  updateCoachEducation: (request: CoachEducationUpdateRequest) =>
    httpClient.put<CoachProfileResponse>("/api/v1/coach/profile/me/education", request),
  uploadProfileImage: async (file: File): Promise<MediaAssetResponse> => {
    const presign = await httpClient.post<MediaPresignResponse>("/api/v1/media/uploads/presign", {
      mediaType: "PROFILE_IMAGE",
      contentType: file.type,
      sizeBytes: file.size,
      originalFilename: file.name,
    });
    const upload = await fetch(presign.uploadUrl, {
      method: "PUT",
      body: file,
      headers: presign.requiredHeaders,
    });
    if (!upload.ok) throw new Error("PROFILE_IMAGE_UPLOAD_FAILED");
    return httpClient.post<MediaAssetResponse>(`/api/v1/media/uploads/${presign.assetId}/complete`);
  },
};
