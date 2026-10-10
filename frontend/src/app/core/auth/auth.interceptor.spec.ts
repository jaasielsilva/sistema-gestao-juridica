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
    sessionStorage.setItem('app.session', JSON.stringify({ token: 'abc', expiresAt: Date.now() + 60_000 }));
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'login', children: [] }]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => {
    sessionStorage.clear();
    http.verify();
  });

  it('anexa Bearer nas chamadas a API', () => {
    client.get('/api/v1/algo').subscribe();
    expect(http.expectOne('/api/v1/algo').request.headers.get('Authorization')).toBe('Bearer abc');
  });

  it('nao envia o token para outros dominios', () => {
    client.get('https://outro.com/x').subscribe();
    expect(http.expectOne('https://outro.com/x').request.headers.has('Authorization')).toBe(false);
  });

  it('401 em rota protegida encerra a sessao', () => {
    client.get('/api/v1/algo').subscribe({ error: () => undefined });
    http.expectOne('/api/v1/algo').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.hasValidSession()).toBe(false);
  });

  it('401 no login nao encerra a sessao (credencial invalida)', () => {
    client.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });
    http.expectOne('/api/v1/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.hasValidSession()).toBe(true);
  });
});
