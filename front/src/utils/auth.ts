import type { AuthResponseDto } from "../types/auth";

export interface StoredAuth extends AuthResponseDto {
  issuedAt: number;
}

const AUTH_STORAGE_KEY = "beachcheck_auth";

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

  storage?.setItem(AUTH_STORAGE_KEY, JSON.stringify(stored));
  return stored;
}

export function loadAuth(): StoredAuth | null {
  const storage = getStorage();
  const raw = storage?.getItem(AUTH_STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as StoredAuth;
    if (!parsed?.accessToken || !parsed?.user) {
      return null;
    }

    return parsed;
  } catch (error) {
    return null;
  }
}

export function clearAuth(): void {
  const storage = getStorage();
  storage?.removeItem(AUTH_STORAGE_KEY);
}

export function isAccessTokenExpired(auth: StoredAuth, bufferSeconds = 30): boolean {
  const expiresAt = auth.issuedAt + auth.expiresIn * 1000;
  return Date.now() + bufferSeconds * 1000 >= expiresAt;
}
