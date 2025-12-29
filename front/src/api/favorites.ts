// front/src/api/favorites.ts
import { Beach } from '@/types/beach';

const DEFAULT_API_BASE_URL = '';
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? DEFAULT_API_BASE_URL;
const API_BASE = `${API_BASE_URL}/api/favorites`;

function getAuthToken(): string | null {
  return localStorage.getItem('accessToken');
}

function createAuthHeaders(): HeadersInit {
  const token = getAuthToken();
  const headers: HeadersInit = {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return headers;
}

export const favoritesApi = {
  /**
   * 내 찜 목록 조회
   * GET /api/favorites
   */
  async getMyFavorites(): Promise<Beach[]> {
    const res = await fetch(API_BASE, {
      headers: createAuthHeaders()
    });

    if (!res.ok) {
      throw new Error(`Failed to fetch favorites: ${res.status} ${res.statusText}`);
    }

    return res.json();
  },

  /**
   * 찜 추가
   * POST /api/favorites/{beachId}
   */
  async addFavorite(beachId: string): Promise<{ message: string; isFavorite: boolean }> {
    const res = await fetch(`${API_BASE}/${beachId}`, {
      method: 'POST',
      headers: createAuthHeaders()
    });

    if (!res.ok) {
      throw new Error(`Failed to add favorite: ${res.status} ${res.statusText}`);
    }

    return res.json();
  },

  /**
   * 찜 제거
   * DELETE /api/favorites/{beachId}
   */
  async removeFavorite(beachId: string): Promise<{ message: string; isFavorite: boolean }> {
    const res = await fetch(`${API_BASE}/${beachId}`, {
      method: 'DELETE',
      headers: createAuthHeaders()
    });

    if (!res.ok) {
      throw new Error(`Failed to remove favorite: ${res.status} ${res.statusText}`);
    }

    return res.json();
  },

  /**
   * 찜 토글 (추가/제거)
   * PUT /api/favorites/{beachId}/toggle
   */
  async toggleFavorite(beachId: string): Promise<{ message: string; isFavorite: boolean }> {
    const res = await fetch(`${API_BASE}/${beachId}/toggle`, {
      method: 'PUT',
      headers: createAuthHeaders()
    });

    if (!res.ok) {
      throw new Error(`Failed to toggle favorite: ${res.status} ${res.statusText}`);
    }

    return res.json();
  },

  /**
   * 찜 여부 확인
   * GET /api/favorites/{beachId}/check
   */
  async checkFavorite(beachId: string): Promise<boolean> {
    const res = await fetch(`${API_BASE}/${beachId}/check`, {
      headers: createAuthHeaders()
    });

    if (!res.ok) {
      throw new Error(`Failed to check favorite: ${res.status} ${res.statusText}`);
    }

    const data = await res.json();
    return data.isFavorite;
  }
};