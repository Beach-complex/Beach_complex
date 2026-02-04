import type { ProblemDetail } from "@/types/auth";
import { loadAuth } from "@/utils/auth";

const DEFAULT_API_BASE_URL = "";
const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
  DEFAULT_API_BASE_URL;

export type ReservationCreateRequest = {
  reservedAtUtc: string;
  eventId?: string | null;
};

export type ReservationResponse = {
  reservationId: string;
  status: string;
  reservedAtUtc: string | number;
  beachId: string;
  eventId?: string | null;
  createdAtUtc: string | number;
};

export class ApiError extends Error {
  readonly status?: number;
  readonly code?: string;
  readonly details?: Record<string, unknown>;

  constructor(message: string, status?: number, code?: string, details?: Record<string, unknown>) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

function createAuthHeaders(): HeadersInit {
  const auth = loadAuth();
  const headers: HeadersInit = {
    Accept: "application/json",
    "Content-Type": "application/json",
  };

  if (auth?.accessToken) {
    // TODO(OAuth): tokenType이 Bearer 고정이 아닐 수 있으므로 저장된 tokenType을 사용하도록 통일.
    headers.Authorization = `Bearer ${auth.accessToken}`;
  }

  return headers;
}

async function parseApiError(response: Response): Promise<ApiError> {
  let message = `${response.status} ${response.statusText}`;
  let code: string | undefined;
  let details: Record<string, unknown> | undefined;

  try {
    const contentType = response.headers.get("content-type") ?? "";
    if (contentType.includes("json")) {
      const data = (await response.json()) as ProblemDetail & {
        code?: string;
        details?: Record<string, unknown>;
      };
      message = data.detail ?? data.title ?? message;
      code = data.code ?? data.title;
      details = data.details ?? (data.errors as Record<string, unknown> | undefined);
    } else {
      const text = await response.text();
      if (text) {
        message = text;
      }
    }
  } catch (error) {
  }

  return new ApiError(message, response.status, code, details);
}

export async function createReservation(
  beachId: string,
  payload: ReservationCreateRequest,
): Promise<ReservationResponse> {
  const response = await fetch(
    `${API_BASE_URL}/api/beaches/${encodeURIComponent(beachId)}/reservations`,
    {
      method: "POST",
      headers: createAuthHeaders(),
      body: JSON.stringify(payload),
    },
  );

  if (!response.ok) {
    throw await parseApiError(response);
  }

  return (await response.json()) as ReservationResponse;
}

export async function deleteReservation(
  beachId: string,
  reservationId: string,
): Promise<void> {
  const response = await fetch(
    `${API_BASE_URL}/api/beaches/${encodeURIComponent(beachId)}/reservations/${encodeURIComponent(reservationId)}`,
    {
      method: "DELETE",
      headers: createAuthHeaders(),
    },
  );

  if (!response.ok) {
    throw await parseApiError(response);
  }
}

export async function getMyReservations(): Promise<ReservationResponse[]> {
  const response = await fetch(`${API_BASE_URL}/api/beaches/reservations`, {
    headers: createAuthHeaders(),
  });

  if (!response.ok) {
    throw await parseApiError(response);
  }

  return (await response.json()) as ReservationResponse[];
}
