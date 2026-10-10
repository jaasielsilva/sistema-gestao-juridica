import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, finalize, firstValueFrom, map, of, shareReplay, switchMap, tap } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { LoginRequest, LoginResponse, LoginResult, MfaSetup, Message, User } from './auth.models';

const BASE = APP_CONFIG.apiBase;

/**
 * Sessao do usuario.
 * - O access token (curto) vive SO em memoria: nao ha token em localStorage/sessionStorage, entao um XSS nao
 *   consegue le-lo de la.
 * - O refresh token vive em cookie HttpOnly (o JavaScript nem enxerga). Ao abrir/recarregar a pagina,
 *   restoreSession() usa esse cookie para obter um access token novo.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly accessToken = signal<string | null>(null);
  private readonly _user = signal<User | null>(null);
  private refreshInFlight$: Observable<string> | null = null;

  readonly user = this._user.asReadonly();
  readonly isAdmin = computed(() => this._user()?.papel === 'ADMIN');

  /** Token para o interceptor. */
  get token(): string | null {
    return this.accessToken();
  }

  /** Ha sessao ativa neste navegador? (Detalhe de UX; a validacao real e sempre do backend.) */
  hasSession(): boolean {
    return this.accessToken() !== null;
  }

  /** Chamado uma vez na inicializacao do app: tenta reaproveitar a sessao pelo cookie de refresh. */
  restoreSession(): Promise<void> {
    return firstValueFrom(
      this.refresh().pipe(
        switchMap(() => this.loadUser()),
        map(() => undefined),
        catchError(() => of(undefined)),
      ),
    );
  }

  login(credentials: LoginRequest): Observable<LoginResult> {
    return this.http.post<LoginResponse>(`${BASE}/auth/login`, credentials).pipe(switchMap((res) => this.afterLogin(res)));
  }

  verifyMfa(mfaToken: string, codigo: string): Observable<LoginResult> {
    return this.http.post<LoginResponse>(`${BASE}/auth/mfa/verify`, { mfaToken, codigo }).pipe(switchMap((res) => this.afterLogin(res)));
  }

  /** Renova o access token. Chamadas simultaneas compartilham UMA requisicao (o refresh token e de uso unico). */
  refresh(): Observable<string> {
    if (!this.refreshInFlight$) {
      this.refreshInFlight$ = this.http.post<LoginResponse>(`${BASE}/auth/refresh`, {}).pipe(
        map((res) => {
          if (!res.accessToken) throw new Error('Resposta de refresh sem token');
          this.accessToken.set(res.accessToken);
          return res.accessToken;
        }),
        finalize(() => (this.refreshInFlight$ = null)),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    }
    return this.refreshInFlight$;
  }

  /** Busca o usuario logado no servidor (fonte da verdade do nome, papel e estado do MFA). */
  loadUser(): Observable<User> {
    return this.http.get<User>(`${BASE}/auth/me`).pipe(tap((u) => this._user.set(u)));
  }

  /** Encerra a sessao no servidor (revoga na hora) e limpa o estado local. */
  logout(): void {
    this.http.post(`${BASE}/auth/logout`, {}).pipe(catchError(() => of(null))).subscribe();
    this.clearLocal();
  }

  logoutAllDevices(): Observable<unknown> {
    return this.http.post(`${BASE}/auth/logout-all`, {}).pipe(tap(() => this.clearLocal()));
  }

  /** Limpa a sessao local e vai para o login. Usado tambem quando o servidor recusa a renovacao. */
  clearLocal(): void {
    this.accessToken.set(null);
    this._user.set(null);
    void this.router.navigate(['/login']);
  }

  // ---------- fluxos de conta (publicos) ----------

  register(body: { nome: string; email: string; senha: string }): Observable<User> {
    return this.http.post<User>(`${BASE}/auth/register`, body);
  }

  verifyEmail(token: string): Observable<Message> {
    return this.http.post<Message>(`${BASE}/auth/verify-email`, { token });
  }

  resendVerification(email: string): Observable<Message> {
    return this.http.post<Message>(`${BASE}/auth/resend-verification`, { email });
  }

  forgotPassword(email: string): Observable<Message> {
    return this.http.post<Message>(`${BASE}/auth/forgot-password`, { email });
  }

  resetPassword(token: string, novaSenha: string): Observable<Message> {
    return this.http.post<Message>(`${BASE}/auth/reset-password`, { token, novaSenha });
  }

  registrationEnabled(): Observable<boolean> {
    return this.http.get<{ registrationEnabled: boolean }>(`${BASE}/auth/config`).pipe(
      map((c) => c.registrationEnabled),
      catchError(() => of(false)),
    );
  }

  // ---------- conta (autenticado) ----------

  changePassword(senhaAtual: string, novaSenha: string): Observable<Message> {
    return this.http.post<Message>(`${BASE}/auth/change-password`, { senhaAtual, novaSenha });
  }

  mfaSetup(): Observable<MfaSetup> {
    return this.http.post<MfaSetup>(`${BASE}/auth/mfa/setup`, {});
  }

  mfaEnable(codigo: string): Observable<string[]> {
    return this.http.post<{ codigos: string[] }>(`${BASE}/auth/mfa/enable`, { codigo }).pipe(
      switchMap((r) => this.loadUser().pipe(map(() => r.codigos))),
    );
  }

  mfaDisable(senha: string, codigo: string): Observable<Message> {
    return this.http.post<Message>(`${BASE}/auth/mfa/disable`, { senha, codigo }).pipe(
      switchMap((r) => this.loadUser().pipe(map(() => r))),
    );
  }

  private afterLogin(res: LoginResponse): Observable<LoginResult> {
    if (res.mfaRequired && res.mfaToken) {
      return of({ status: 'mfa', mfaToken: res.mfaToken } as LoginResult);
    }
    if (!res.accessToken) throw new Error('Resposta de login sem token');
    this.accessToken.set(res.accessToken);
    return this.loadUser().pipe(map(() => ({ status: 'done' }) as LoginResult));
  }
}
