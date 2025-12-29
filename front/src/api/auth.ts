import type {
  AuthResponseDto,
  ProblemDetail,
  TokenResponseDto,
  UserResponseDto,
} from "../types/auth";

const DEFAULT_API_BASE_URL = "";
const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
  DEFAULT_API_BASE_URL;

export type LogInRequest = {
  email: string;
  password: string;
};

export type SignUpRequest = {
  email: string;
  password: string;
  name: string;
};

export type RefreshTokenRequest = {
  refreshToken: string;
};

export class ApiError extends Error {
  readonly status?: number;
  readonly fieldErrors?: Record<string, string>;

  constructor(message: string, status?: number, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

function buildUrl(path: string): string {
  return `${API_BASE_URL}${path}`;
}

async function parseApiError(response: Response): Promise<ApiError> {
  let message = `${response.status} ${response.statusText}`;
  let fieldErrors: Record<string, string> | undefined;

  try {
    const contentType = response.headers.get("content-type") ?? "";
    if (contentType.includes("application/json")) {
      const data = (await response.json()) as ProblemDetail;
      message = data.detail ?? data.title ?? message;
      if (data.errors && typeof data.errors === "object") {
        fieldErrors = data.errors;
      }
    } else {
      const text = await response.text();
      if (text) {
        message = text;
      }
    }
  } catch (error) {
  }

  return new ApiError(message, response.status, fieldErrors);
}

async function requestJson<T>(
  path: string,
  options: RequestInit & { body?: unknown } = {},
): Promise<T> {
  const { body, headers, ...rest } = options;
  const response = await fetch(buildUrl(path), {
    ...rest,
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });

  if (!response.ok) {
    throw await parseApiError(response);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return (await response.json()) as T;
}

export async function logIn(payload: LogInRequest): Promise<AuthResponseDto> {
  return requestJson<AuthResponseDto>("/api/auth/login", {
    method: "POST",
    body: payload,
  });
}

export async function signUp(payload: SignUpRequest): Promise<UserResponseDto> {
  return requestJson<UserResponseDto>("/api/auth/signup", {
    method: "POST",
    body: payload,
  });
}

export async function refreshToken(
  payload: RefreshTokenRequest,
): Promise<TokenResponseDto> {
  return requestJson<TokenResponseDto>("/api/auth/refresh", {
    method: "POST",
    body: payload,
  });
}
