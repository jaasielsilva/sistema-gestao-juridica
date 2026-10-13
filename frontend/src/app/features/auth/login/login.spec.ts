import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Login } from './login';

describe('Login', () => {
  beforeEach(() => {
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

  async function render(registration = true) {
    const fixture = TestBed.createComponent(Login);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne('/api/v1/auth/config').flush({ registrationEnabled: registration });
    await fixture.whenStable();
    return { fixture, http, el: fixture.nativeElement as HTMLElement };
  }

  it('nao envia o formulario invalido', async () => {
    const { fixture, http, el } = await render();

    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    http.expectNone('/api/v1/auth/login');
    // A validacao e nativa do navegador (balao "Preencha este campo"), igual ao SaaS Monitor.
    expect(el.querySelector<HTMLInputElement>('input[type=email]')!.validity.valueMissing).toBe(true);
  });

  it('so mostra "Criar conta" quando o cadastro esta habilitado no servidor', async () => {
    const on = await render(true);
    expect(on.el.textContent).toContain('Criar conta');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    const off = await render(false);
    expect(off.el.textContent).not.toContain('Criar conta');
  });

  it('mostra a mensagem do servidor quando as credenciais sao invalidas', async () => {
    const { fixture, http, el } = await render();

    typeInto(el, 'input[type=email]', 'a@b.com');
    typeInto(el, 'input[type=password]', 'errada');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    http
      .expectOne('/api/v1/auth/login')
      .flush({ status: 401, message: 'Credenciais invalidas' }, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(el.querySelector('[role=alert]')?.textContent).toContain('Credenciais invalidas');
  });

  it('passa para o codigo MFA quando o servidor exige o segundo fator', async () => {
    const { fixture, http, el } = await render();

    typeInto(el, 'input[type=email]', 'a@b.com');
    typeInto(el, 'input[type=password]', 'certa-certa');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http.expectOne('/api/v1/auth/login').flush({ mfaRequired: true, mfaToken: 'mfa-tok' });
    await fixture.whenStable();

    expect(el.textContent).toContain('Verificação em duas etapas');
    expect(el.querySelector('input[type=password]')).toBeNull();

    typeInto(el, 'input[formcontrolname=codigo]', '123456');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    const req = http.expectOne('/api/v1/auth/mfa/verify');
    expect(req.request.body).toEqual({ mfaToken: 'mfa-tok', codigo: '123456' });
  });

  it('oferece reenviar a confirmacao quando o servidor recusa por e-mail nao verificado', async () => {
    const { fixture, http, el } = await render();

    typeInto(el, 'input[type=email]', 'a@b.com');
    typeInto(el, 'input[type=password]', 'certa-certa');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    http
      .expectOne('/api/v1/auth/login')
      .flush({ status: 403, message: 'Confirme seu e-mail antes de entrar' }, { status: 403, statusText: 'Forbidden' });
    await fixture.whenStable();

    expect(el.textContent).toContain('Confirme seu e-mail antes de entrar');
    expect(el.textContent).toContain('Reenviar e-mail de confirmação');
  });
});
