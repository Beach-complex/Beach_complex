// front/src/api/favorites.ts
import { Beach } from '@/types/beach';
import { loadAuth } from '@/utils/auth';

const DEFAULT_API_BASE_URL = '';
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? DEFAULT_API_BASE_URL;
const API_BASE = `${API_BASE_URL}/api/favorites`;

function getAuthToken(): string | null {
  const auth = loadAuth();
  return auth?.accessToken ?? null;
}

function createAuthHeaders(): HeadersInit {
  const token = getAuthToken();
  const headers: HeadersInit = {
    'Accept': 'application/json',
    'Content-Type': 'application/json',
  };

  if (token) {
    // TODO(OAuth): tokenType이 Bearer 고정이 아닐 수 있으므로 저장된 tokenType을 사용하도록 통일.
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
    const token = getAuthToken();
    console.log('🔍 [getMyFavorites] token:', token ? `${token.substring(0, 20)}...` : 'null');
    console.log('🔍 [getMyFavorites] URL:', API_BASE);

    const res = await fetch(API_BASE, {
      headers: createAuthHeaders()
    });

    console.log('🔍 [getMyFavorites] response status:', res.status);

    if (!res.ok) {
      const errorText = await res.text();
      console.error('🔍 [getMyFavorites] error response:', errorText);
      throw new Error(`Failed to fetch favorites: ${res.status} ${res.statusText}`);
    }

    const data = await res.json();
    console.log('🔍 [getMyFavorites] success, count:', data.length);
    return data;
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
    const token = getAuthToken();
    console.log('🔍 [toggleFavorite] beachId:', beachId);
    console.log('🔍 [toggleFavorite] token:', token ? `${token.substring(0, 20)}...` : 'null');
    console.log('🔍 [toggleFavorite] URL:', `${API_BASE}/${beachId}/toggle`);

    const res = await fetch(`${API_BASE}/${beachId}/toggle`, {
      method: 'PUT',
      headers: createAuthHeaders()
    });

    console.log('🔍 [toggleFavorite] response status:', res.status);

    if (!res.ok) {
      const errorText = await res.text();
      console.error('🔍 [toggleFavorite] error response:', errorText);
      console.error('🔍 [toggleFavorite] response headers:', Object.fromEntries(res.headers.entries()));
      alert(`❌ 찜 실패: ${res.status}\n\n응답: ${errorText.substring(0, 200)}`);
      throw new Error(`Failed to toggle favorite: ${res.status} ${res.statusText}`);
    }

    const data = await res.json();
    console.log('🔍 [toggleFavorite] success:', data);
    return data;
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
