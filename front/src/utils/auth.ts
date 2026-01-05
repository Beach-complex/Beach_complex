import type { AuthResponseDto } from "../types/auth";

export interface StoredAuth extends AuthResponseDto {
  issuedAt: number;
}

const AUTH_STORAGE_KEY = "beachcheck_auth";
let cachedAuth: StoredAuth | null = null;

function getStorage(): Storage | null {
  if (typeof window === "undefined") {
    return null;
  }

  return window.localStorage;
}

export function saveAuth(auth: AuthResponseDto): StoredAuth {
  const storage = getStorage();
  const stored: StoredAuth = {
    ...auth,
    issuedAt: Date.now(),
  };

  cachedAuth = stored;
  storage?.setItem(AUTH_STORAGE_KEY, JSON.stringify(stored));
  return stored;
}

export function loadAuth(): StoredAuth | null {
  return cachedAuth;
}

export function clearAuth(): void {
  const storage = getStorage();
  cachedAuth = null;
  storage?.removeItem(AUTH_STORAGE_KEY);
}

export function isAccessTokenExpired(auth: StoredAuth, bufferSeconds = 30): boolean {
  const expiresAt = auth.issuedAt + auth.expiresIn * 1000;
  return Date.now() + bufferSeconds * 1000 >= expiresAt;
}
