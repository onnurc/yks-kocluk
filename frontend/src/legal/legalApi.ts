import { httpClient } from "../api/httpClient";
import type { LegalDocument, LegalDocumentType } from "./legalTypes";

export const legalApi = {
  listCurrent: (): Promise<LegalDocument[]> =>
    httpClient.get<LegalDocument[]>("/api/v1/legal-documents"),

  getCurrent: (type: LegalDocumentType): Promise<LegalDocument> =>
    httpClient.get<LegalDocument>(`/api/v1/legal-documents/${type}/current`),
};
