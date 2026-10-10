import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'login', children: [] }]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => http.verify());

  /** Faz login "de verdade" pelo AuthService (a sessao fica em memoria). */
  function loginAs(token: string) {
    auth.login({ email: 'a@b.com', senha: 'x' }).subscribe();
    http.expectOne('/api/v1/auth/login').flush({ accessToken: token, tokenType: 'Bearer', expiresInSeconds: 60 });
    http
      .expectOne('/api/v1/auth/me')
      .flush({ id: 1, nome: 'M', email: 'a@b.com', papel: 'USER', tenantId: 1, emailVerificado: true, mfaAtivo: false });
  }

  it('anexa Bearer e X-Requested-With nas chamadas a API', () => {
    loginAs('abc');
    client.get('/api/v1/algo').subscribe();

    const req = http.expectOne('/api/v1/algo').request;
    expect(req.headers.get('Authorization')).toBe('Bearer abc');
    expect(req.headers.get('X-Requested-With')).toBe('XMLHttpRequest');
  });

  it('nao envia o token para outros dominios', () => {
    loginAs('abc');
    client.get('https://outro.com/x').subscribe();

    const req = http.expectOne('https://outro.com/x').request;
    expect(req.headers.has('Authorization')).toBe(false);
    expect(req.headers.has('X-Requested-With')).toBe(false);
  });

  it('401 renova pelo cookie e repete a requisicao com o token novo', () => {
    loginAs('velho');
    let body: unknown;
    client.get('/api/v1/algo').subscribe((b) => (body = b));

    http.expectOne('/api/v1/algo').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'novo', tokenType: 'Bearer', expiresInSeconds: 60 });
    const retried = http.expectOne('/api/v1/algo');
    expect(retried.request.headers.get('Authorization')).toBe('Bearer novo');
    retried.flush({ ok: true });

    expect(body).toEqual({ ok: true });
    expect(auth.hasSession()).toBe(true);
  });

  it('varias requisicoes que tomam 401 juntas disparam UM refresh so', () => {
    loginAs('velho');
    client.get('/api/v1/a').subscribe();
    client.get('/api/v1/b').subscribe();

    http.expectOne('/api/v1/a').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/b').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'novo', tokenType: 'Bearer', expiresInSeconds: 60 });

    http.expectOne('/api/v1/a').flush({});
    http.expectOne('/api/v1/b').flush({});
  });

  it('se a renovacao tambem falhar, encerra a sessao', () => {
    loginAs('velho');
    let failed = false;
    client.get('/api/v1/algo').subscribe({ error: () => (failed = true) });

    http.expectOne('/api/v1/algo').flush({}, { status: 401, statusText: 'Unauthorized' });
    http.expectOne('/api/v1/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.hasSession()).toBe(false);
  });

  it('401 no login nao tenta renovar (credencial invalida)', () => {
    let status = 0;
    client.post('/api/v1/auth/login', {}).subscribe({ error: (e) => (status = e.status) });

    http.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(status).toBe(401);
    http.expectNone('/api/v1/auth/refresh');
  });
});
