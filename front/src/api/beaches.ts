import type { Beach, BeachStatus } from "@/types/beach";

const DEFAULT_API_BASE_URL = "";
const API_BASE_URL =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ??
  DEFAULT_API_BASE_URL;

const STATUS_MAP: Record<string, BeachStatus> = {
  busy: "busy",
  normal: "normal",
  free: "free",
  open: "normal",
  closed: "unknown",
};

const toStatus = (value: unknown): BeachStatus => {
  if (typeof value !== "string") {
    return "unknown";
  }

  const normalized = value.trim().toLowerCase();
  return STATUS_MAP[normalized] ?? "unknown";
};

const toNumber = (value: unknown): number => {
  if (typeof value === "number") {
    return value;
  }

  if (typeof value === "string") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }

  return Number.NaN;
};

export async function fetchBeachByCode(code: string): Promise<Beach | null> {
  const response = await fetch(`${API_BASE_URL}/api/beaches/${encodeURIComponent(code)}`);

  if (response.status === 404) {
    return null;
  }

  if (!response.ok) {
    throw new Error(`Failed to load beach: ${response.status}`);
  }

  const payload = await response.json();

  return {
    id: typeof payload.id === "string" ? payload.id : String(payload.id ?? ""),
    code: typeof payload.code === "string" ? payload.code : code,
    name: typeof payload.name === "string" ? payload.name : "",
    status: toStatus(payload.status),
    latitude: toNumber(payload.latitude),
    longitude: toNumber(payload.longitude),
    updatedAt: payload.updatedAt ?? undefined,
    tag: typeof payload.tag === "string" ? payload.tag : null,
    isFavorite: Boolean(payload.isFavorite),
  };
}

export async function fetchBeaches(): Promise<Beach[]> {
  const response = await fetch(`${API_BASE_URL}/api/beaches`);

  if (!response.ok) {
    throw new Error(`Failed to load beaches: ${response.status}`);
  }

  const payload = await response.json();
  if (!Array.isArray(payload)) {
    return [];
  }

  return payload.map((item) => ({
    id: typeof item.id === "string" ? item.id : String(item.id ?? ""),
    code: typeof item.code === "string" ? item.code : "",
    name: typeof item.name === "string" ? item.name : "",
    status: toStatus(item.status),
    latitude: toNumber(item.latitude),
    longitude: toNumber(item.longitude),
    updatedAt: item.updatedAt ?? undefined,
    tag: typeof item.tag === "string" ? item.tag : null,
    isFavorite: Boolean(item.isFavorite),
  }));
}
