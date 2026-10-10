import { HttpErrorResponse, HttpHandlerFn, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { AuthService } from './auth.service';

/** Endpoints em que um 401 significa "credencial/codigo invalido" ou "sem sessao", e nao "access token expirou". */
const NO_REFRESH_ON_401 = [
  '/auth/login',
  '/auth/refresh',
  '/auth/logout',
  '/auth/mfa/verify',
  '/auth/register',
  '/auth/forgot-password',
  '/auth/reset-password',
  '/auth/verify-email',
  '/auth/resend-verification',
];

/**
 * - Marca toda chamada a API com X-Requested-With (exigido nos endpoints que usam o cookie de refresh: defesa CSRF).
 * - Anexa o Bearer.
 * - Se a API responder 401 (access token expirado/sessao revogada), tenta UMA renovacao pelo cookie e repete a
 *   requisicao; se a renovacao falhar, encerra a sessao.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(APP_CONFIG.apiBase)) return next(req);

  const auth = inject(AuthService);
  const skipRefresh = NO_REFRESH_ON_401.some((p) => req.url.endsWith(p));

  return next(decorate(req, auth.token)).pipe(
    catchError((err: unknown) => {
      if (!(err instanceof HttpErrorResponse) || err.status !== 401 || skipRefresh) {
        return throwError(() => err);
      }
      return auth.refresh().pipe(
        switchMap((token) => retry(req, token, next)),
        catchError((refreshErr: unknown) => {
          auth.clearLocal();
          return throwError(() => refreshErr);
        }),
      );
    }),
  );
};

function decorate(req: HttpRequest<unknown>, token: string | null): HttpRequest<unknown> {
  const headers: Record<string, string> = { 'X-Requested-With': 'XMLHttpRequest' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  return req.clone({ setHeaders: headers });
}

function retry(req: HttpRequest<unknown>, token: string, next: HttpHandlerFn) {
  return next(decorate(req, token));
}
