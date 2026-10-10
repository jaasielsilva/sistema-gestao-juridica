import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';

const providers = () => [provideRouter([{ path: 'login', children: [] }]), provideHttpClient(), provideHttpClientTesting()];

describe('AuthService', () => {
  beforeEach(() => sessionStorage.clear());

  it('login guarda a sessao e carrega o usuario', () => {
    TestBed.configureTestingModule({ providers: providers() });
    const service = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);

    let nome = '';
    service.login({ email: 'a@b.com', senha: 'x' }).subscribe((u) => (nome = u.nome));
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'tok', tokenType: 'Bearer', expiresInSeconds: 60 });
    http.expectOne('/api/v1/auth/me').flush({ id: 1, nome: 'Maria', email: 'a@b.com', papel: 'USER' });

    expect(nome).toBe('Maria');
    expect(service.hasValidSession()).toBe(true);
    expect(service.token).toBe('tok');
    expect(service.user()?.email).toBe('a@b.com');
    http.verify();
  });

  it('logout limpa sessao e usuario', () => {
    TestBed.configureTestingModule({ providers: providers() });
    const service = TestBed.inject(AuthService);
    const http = TestBed.inject(HttpTestingController);

    service.login({ email: 'a@b.com', senha: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'tok', tokenType: 'Bearer', expiresInSeconds: 60 });
    http.expectOne('/api/v1/auth/me').flush({ id: 1, nome: 'M', email: 'a@b.com', papel: 'USER' });

    service.logout();

    expect(service.hasValidSession()).toBe(false);
    expect(service.token).toBeNull();
    expect(service.user()).toBeNull();
    expect(sessionStorage.getItem('app.session')).toBeNull();
  });

  it('sessao expirada nao e valida', () => {
    sessionStorage.setItem('app.session', JSON.stringify({ token: 't', expiresAt: Date.now() - 1000 }));
    TestBed.configureTestingModule({ providers: providers() });
    const service = TestBed.inject(AuthService);

    expect(service.hasValidSession()).toBe(false);
    expect(service.token).toBeNull();
  });
});
