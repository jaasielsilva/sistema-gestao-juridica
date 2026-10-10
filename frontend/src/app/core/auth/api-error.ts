import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from './auth.models';

/** Converte qualquer erro HTTP em uma mensagem amigavel, sem expor detalhes tecnicos. */
export function apiErrorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Não foi possível conectar ao servidor. Tente novamente.';
    const body = err.error as Partial<ApiError> | null;
    if (err.status < 500 && typeof body?.message === 'string') return body.message;
  }
  return 'Ocorreu um erro inesperado. Tente novamente.';
}
