import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { PrazoFiltro, dataBr } from './prazo.models';
import { PrazoService } from './prazo.service';

describe('PrazoService', () => {
  let service: PrazoService;
  let http: HttpTestingController;
  const base = `${APP_CONFIG.apiBase}/prazos`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(PrazoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista enviando só os filtros preenchidos', () => {
    const filtro: PrazoFiltro = { situacao: 'VENCIDO', dias: 7, categoria: 'RECURSOS', responsavelId: '', page: 2, size: 25 };
    service.listar(filtro).subscribe();

    const req = http.expectOne((r) => r.url === base);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('situacao')).toBe('VENCIDO');
    expect(req.request.params.get('dias')).toBe('7');
    expect(req.request.params.get('categoria')).toBe('RECURSOS');
    expect(req.request.params.has('responsavelId')).toBe(false);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
    req.flush({ content: [], page: 2, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('período "hoje" (0 dias) é enviado, não confundido com sem limite', () => {
    service.listar({ situacao: '', dias: 0, categoria: '', responsavelId: '', page: 0, size: 10 }).subscribe();
    const req = http.expectOne((r) => r.url === base);
    expect(req.request.params.get('dias')).toBe('0');
    req.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('cumpre, reabre e exclui pelo id', () => {
    service.cumprir(5).subscribe();
    const cumprir = http.expectOne(`${base}/5/cumprir`);
    expect(cumprir.request.method).toBe('POST');
    cumprir.flush({});

    service.reabrir(5).subscribe();
    const reabrir = http.expectOne(`${base}/5/reabrir`);
    expect(reabrir.request.method).toBe('POST');
    reabrir.flush({});

    service.excluir(5).subscribe();
    const excluir = http.expectOne(`${base}/5`);
    expect(excluir.request.method).toBe('DELETE');
    excluir.flush(null);
  });

  it('cria e atualiza com o corpo informado', () => {
    const body = { processoId: 1, tipo: 'CONTESTACAO', dataVencimento: '2026-12-01' };
    service.criar(body).subscribe();
    const criar = http.expectOne(base);
    expect(criar.request.method).toBe('POST');
    expect(criar.request.body).toEqual(body);
    criar.flush({});

    service.atualizar(9, body).subscribe();
    const atualizar = http.expectOne(`${base}/9`);
    expect(atualizar.request.method).toBe('PUT');
    atualizar.flush({});
  });

  it('busca resumo e catálogo de tipos', () => {
    service.resumo().subscribe();
    http.expectOne(`${base}/resumo`).flush({ hoje: 1, proximos: 2, vencidos: 3, cumpridosUltimos30Dias: 4 });
    service.tipos().subscribe();
    http.expectOne(`${base}/tipos`).flush([]);
  });

  it('formata a data sem deslocar o dia pelo fuso', () => {
    expect(dataBr('2026-11-20')).toBe('20/11/2026');
    expect(dataBr(null)).toBe('—');
  });
});
