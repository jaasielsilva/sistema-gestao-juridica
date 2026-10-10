import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { authGuard, guestGuard } from './auth.guards';

describe('guards', () => {
  const setup = (session: object | null) => {
    sessionStorage.clear();
    if (session) sessionStorage.setItem('app.session', JSON.stringify(session));
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  };
  const run = (guard: typeof authGuard, url = '/inicio') =>
    TestBed.runInInjectionContext(() => guard({} as never, { url } as never));

  it('authGuard redireciona para /login guardando o destino quando nao ha sessao', () => {
    setup(null);
    const result = run(authGuard) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login?returnUrl=%2Finicio');
  });

  it('authGuard libera com sessao valida', () => {
    setup({ token: 't', expiresAt: Date.now() + 60_000 });
    expect(run(authGuard)).toBe(true);
  });

  it('guestGuard manda quem ja esta logado para a raiz', () => {
    setup({ token: 't', expiresAt: Date.now() + 60_000 });
    expect(run(guestGuard)).toBeInstanceOf(UrlTree);
  });
});
