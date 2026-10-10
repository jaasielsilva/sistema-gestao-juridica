import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, switchMap, tap } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { LoginRequest, TokenResponse, User } from './auth.models';
import { Session, TokenStorage } from './token-storage';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly storage = inject(TokenStorage);

  private readonly session = signal<Session | null>(this.storage.read());
  private readonly _user = signal<User | null>(null);

  readonly user = this._user.asReadonly();
  readonly isAdmin = computed(() => this._user()?.papel === 'ADMIN');

  /** Token para o interceptor. Nulo se nao ha sessao ou ela expirou. */
  get token(): string | null {
    return this.hasValidSession() ? this.session()!.token : null;
  }

  hasValidSession(): boolean {
    const s = this.session();
    return !!s && Date.now() < s.expiresAt;
  }

  login(credentials: LoginRequest): Observable<User> {
    return this.http.post<TokenResponse>(`${APP_CONFIG.apiBase}/auth/login`, credentials).pipe(
      tap((res) => {
        const session = { token: res.accessToken, expiresAt: Date.now() + res.expiresInSeconds * 1000 };
        this.storage.write(session);
        this.session.set(session);
      }),
      switchMap(() => this.loadUser()),
    );
  }

  /** Busca o usuario logado no servidor (fonte da verdade do nome e do papel). */
  loadUser(): Observable<User> {
    return this.http.get<User>(`${APP_CONFIG.apiBase}/auth/me`).pipe(tap((u) => this._user.set(u)));
  }

  logout(): void {
    this.storage.clear();
    this.session.set(null);
    this._user.set(null);
    void this.router.navigate(['/login']);
  }
}
