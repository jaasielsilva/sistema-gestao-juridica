import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';

const USER = { id: 1, nome: 'Maria', email: 'a@b.com', papel: 'USER', tenantId: 1, emailVerificado: true, mfaAtivo: false };

function setup() {
  TestBed.configureTestingModule({
    providers: [provideRouter([{ path: 'login', children: [] }]), provideHttpClient(), provideHttpClientTesting()],
  });
  return { service: TestBed.inject(AuthService), http: TestBed.inject(HttpTestingController) };
}

describe('AuthService', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('login guarda o access token SO em memoria e carrega o usuario', () => {
    const { service, http } = setup();
    let result: unknown;
    service.login({ email: 'a@b.com', senha: 'x' }).subscribe((r) => (result = r));

    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'tok', tokenType: 'Bearer', expiresInSeconds: 60 });
    http.expectOne('/api/v1/auth/me').flush(USER);

    expect(result).toEqual({ status: 'done' });
    expect(service.hasSession()).toBe(true);
    expect(service.token).toBe('tok');
    expect(service.user()?.nome).toBe('Maria');
    // nada de token em storage (XSS nao encontra nada para ler)
    expect(sessionStorage.length).toBe(0);
    expect(localStorage.length).toBe(0);
    http.verify();
  });

  it('login com MFA ativo devolve o desafio e NAO cria sessao', () => {
    const { service, http } = setup();
    let result: unknown;
    service.login({ email: 'a@b.com', senha: 'x' }).subscribe((r) => (result = r));

    http.expectOne('/api/v1/auth/login').flush({ mfaRequired: true, mfaToken: 'mfa-tok' });

    expect(result).toEqual({ status: 'mfa', mfaToken: 'mfa-tok' });
    expect(service.hasSession()).toBe(false);
    http.verify();
  });

  it('verifyMfa conclui o login', () => {
    const { service, http } = setup();
    service.verifyMfa('mfa-tok', '123456').subscribe();

    const req = http.expectOne('/api/v1/auth/mfa/verify');
    expect(req.request.body).toEqual({ mfaToken: 'mfa-tok', codigo: '123456' });
    req.flush({ accessToken: 'tok2', tokenType: 'Bearer', expiresInSeconds: 60 });
    http.expectOne('/api/v1/auth/me').flush(USER);

    expect(service.token).toBe('tok2');
    http.verify();
  });

  it('restoreSession usa o cookie de refresh e carrega o usuario', async () => {
    const { service, http } = setup();
    const done = service.restoreSession();

    http.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'novo', tokenType: 'Bearer', expiresInSeconds: 60 });
    // o carregamento do usuario so ocorre depois da renovacao: aguarda a proxima requisicao
    await Promise.resolve();
    http.expectOne('/api/v1/auth/me').flush(USER);
    await done;

    expect(service.hasSession()).toBe(true);
    expect(service.user()?.email).toBe('a@b.com');
    http.verify();
  });

  it('restoreSession sem cookie valido nao quebra a inicializacao do app', async () => {
    const { service, http } = setup();
    const done = service.restoreSession();

    http.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    await done;

    expect(service.hasSession()).toBe(false);
    http.verify();
  });

  it('chamadas simultaneas de refresh compartilham UMA requisicao (refresh token e de uso unico)', () => {
    const { service, http } = setup();
    const tokens: string[] = [];
    service.refresh().subscribe((t) => tokens.push(t));
    service.refresh().subscribe((t) => tokens.push(t));

    http.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'unico', tokenType: 'Bearer', expiresInSeconds: 60 });

    expect(tokens).toEqual(['unico', 'unico']);
    http.verify();
  });

  it('logout avisa o servidor, limpa tudo e volta ao login', () => {
    const { service, http } = setup();
    service.login({ email: 'a@b.com', senha: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush({ accessToken: 'tok', tokenType: 'Bearer', expiresInSeconds: 60 });
    http.expectOne('/api/v1/auth/me').flush(USER);

    service.logout();

    http.expectOne('/api/v1/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    expect(service.hasSession()).toBe(false);
    expect(service.token).toBeNull();
    expect(service.user()).toBeNull();
    http.verify();
  });
});
