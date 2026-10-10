import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Login } from './login';

describe('Login', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  const typeInto = (el: HTMLElement, selector: string, value: string) => {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  it('nao envia o formulario invalido', async () => {
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    const el = fixture.nativeElement as HTMLElement;

    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    http.expectNone('/api/v1/auth/login');
    expect(el.textContent).toContain('Informe o e-mail');
  });

  it('mostra a mensagem do servidor quando as credenciais sao invalidas', async () => {
    const fixture = TestBed.createComponent(Login);
    await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    const el = fixture.nativeElement as HTMLElement;

    typeInto(el, 'input[type=email]', 'a@b.com');
    typeInto(el, 'input[type=password]', 'errada');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    http
      .expectOne('/api/v1/auth/login')
      .flush({ status: 401, message: 'Credenciais invalidas' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Credenciais invalidas');
  });
});
