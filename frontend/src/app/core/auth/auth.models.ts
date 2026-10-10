export type Papel = 'USER' | 'ADMIN';

export interface LoginRequest {
  email: string;
  senha: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface User {
  id: number;
  nome: string;
  email: string;
  papel: Papel;
}

/** Contrato de erro da API (ApiError do backend). */
export interface ApiError {
  status: number;
  message: string;
  correlationId?: string;
  violations?: { field: string; message: string }[];
}
