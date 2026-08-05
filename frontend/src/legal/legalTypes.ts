export type LegalDocumentType =
  | "KVKK_NOTICE"
  | "EXPLICIT_CONSENT"
  | "TERMS_OF_USE"
  | "PRIVACY_POLICY"
  | "COOKIE_POLICY"
  | "PRE_INFORMATION_FORM"
  | "DISTANCE_SALES_AGREEMENT"
  | "REFUND_CANCELLATION_POLICY";

export interface LegalDocument {
  id: number;
  type: LegalDocumentType;
  version: string;
  title: string;
  content: string;
  contentHash: string;
  effectiveAt: string;
}
