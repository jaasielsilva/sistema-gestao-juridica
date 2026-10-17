import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Register } from './register';

describe('Register', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  const typeInto = (el: HTMLElement, selector: string, value: string) => {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  it('nao envia com senhas diferentes ou curtas', async () => {
    const fixture = TestBed.createComponent(Register);
    await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    const el = fixture.nativeElement as HTMLElement;

    typeInto(el, 'input[formcontrolname=nome]', 'Maria');
    typeInto(el, 'input[formcontrolname=organizacao]', 'Org Maria');
    typeInto(el, 'input[formcontrolname=email]', 'm@x.com');
    typeInto(el, 'input[formcontrolname=senha]', 'curta');
    typeInto(el, 'input[formcontrolname=confirmar]', 'outra');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    http.expectNone('/api/v1/auth/register');
  });

  it('cadastra e avisa que o e-mail de confirmacao foi enviado', async () => {
    const fixture = TestBed.createComponent(Register);
    await fixture.whenStable();
    const http = TestBed.inject(HttpTestingController);
    const el = fixture.nativeElement as HTMLElement;

    typeInto(el, 'input[formcontrolname=nome]', 'Maria');
    typeInto(el, 'input[formcontrolname=organizacao]', 'Org Maria');
    typeInto(el, 'input[formcontrolname=email]', 'm@x.com');
    typeInto(el, 'input[formcontrolname=senha]', 'senha-forte-123');
    typeInto(el, 'input[formcontrolname=confirmar]', 'senha-forte-123');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/v1/auth/register');
    expect(req.request.body).toEqual({ nome: 'Maria', organizacao: 'Org Maria', email: 'm@x.com', senha: 'senha-forte-123' });
    req.flush({ message: 'Se o cadastro puder ser concluido, enviaremos instrucoes para o e-mail informado.' });
    await fixture.whenStable();

    expect(el.textContent).toContain('Se o cadastro puder ser concluido');
    expect(el.textContent).toContain('m@x.com');
  });
});
