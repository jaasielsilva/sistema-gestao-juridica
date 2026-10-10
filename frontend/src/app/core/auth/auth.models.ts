export type Papel = 'USER' | 'ADMIN';

export interface LoginRequest {
  email: string;
  senha: string;
}

/** Resposta de login/refresh: ou traz o access token, ou exige o segundo fator (mfaRequired + mfaToken). */
export interface LoginResponse {
  accessToken?: string;
  tokenType?: string;
  expiresInSeconds?: number;
  mfaRequired?: boolean;
  mfaToken?: string;
}

export type LoginResult = { status: 'done' } | { status: 'mfa'; mfaToken: string };

export interface User {
  id: number;
  nome: string;
  email: string;
  papel: Papel;
  tenantId: number;
  emailVerificado: boolean;
  mfaAtivo: boolean;
}

export interface MfaSetup {
  secret: string;
  otpauthUri: string;
}

export interface Message {
  message: string;
}

/** Contrato de erro da API (ApiError do backend). */
export interface ApiError {
  status: number;
  message: string;
  correlationId?: string;
  violations?: { field: string; message: string }[];
}

/** Regra de senha igual a do backend (AuthDtos.SENHA_MIN/SENHA_MAX). */
export const PASSWORD_MIN = 10;
export const PASSWORD_MAX = 72;
