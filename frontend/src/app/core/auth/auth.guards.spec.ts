import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { authGuard, guestGuard } from './auth.guards';
import { AuthService } from './auth.service';

describe('guards', () => {
  const setup = (loggedIn: boolean) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    if (loggedIn) {
      TestBed.inject(AuthService).login({ email: 'a@b.com', senha: 'x' }).subscribe();
      const http = TestBed.inject(HttpTestingController);
      http.expectOne('/api/v1/auth/login').flush({ accessToken: 't', tokenType: 'Bearer', expiresInSeconds: 60 });
      http.expectOne('/api/v1/auth/me').flush({});
    }
  };
  const run = (guard: typeof authGuard, url = '/inicio') =>
    TestBed.runInInjectionContext(() => guard({} as never, { url } as never));

  it('authGuard redireciona para /login guardando o destino quando nao ha sessao', () => {
    setup(false);
    const result = run(authGuard) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/login?returnUrl=%2Finicio');
  });

  it('authGuard libera com sessao', () => {
    setup(true);
    expect(run(authGuard)).toBe(true);
  });

  it('guestGuard manda quem ja esta logado para a raiz e libera visitante', () => {
    setup(true);
    expect(run(guestGuard)).toBeInstanceOf(UrlTree);
  });

  it('guestGuard libera quem nao esta logado', () => {
    setup(false);
    expect(run(guestGuard)).toBe(true);
  });
});
