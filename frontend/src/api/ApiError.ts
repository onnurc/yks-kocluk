export interface FieldError {
  field: string;
  message: string;
}

export class ApiError extends Error {
  status: number;
  title: string;
  detail: string;
  code?: string;
  fieldErrors?: FieldError[];
  nextAllowedAt?: string;

  constructor(
    status: number,
    title: string,
    detail: string,
    code?: string,
    fieldErrors?: FieldError[],
    nextAllowedAt?: string
  ) {
    super(detail || title);
    this.name = "ApiError";
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.nextAllowedAt = nextAllowedAt;
  }
}
