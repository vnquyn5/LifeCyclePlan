export interface ApiErrorBody {
  code?: string;
  message?: string;
  fieldErrors?: Record<string, string>;
  correlationId?: string;
  timestamp?: string;
}

export class ApiRequestError extends Error {
  readonly status?: number;
  readonly code?: string;
  readonly fieldErrors: Record<string, string>;
  readonly correlationId?: string;
  readonly retryAfterSeconds?: number;

  constructor(options: {
    message: string;
    status?: number;
    code?: string;
    fieldErrors?: Record<string, string>;
    correlationId?: string;
    retryAfterSeconds?: number;
  }) {
    super(options.message);
    this.name = "ApiRequestError";
    this.status = options.status;
    this.code = options.code;
    this.fieldErrors = options.fieldErrors ?? {};
    this.correlationId = options.correlationId;
    this.retryAfterSeconds = options.retryAfterSeconds;
  }
}
