// TODO(OAuth): OAuth 도입 시 authProvider/providerUserId 등 클라이언트에 필요한 필드가 있으면 타입 확장.
export interface UserResponseDto {
  id: string;
  email: string;
  name: string;
  role: string;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface AuthResponseDto {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserResponseDto;
}

export interface TokenResponseDto {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string>;
}
