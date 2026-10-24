import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { ProcessoFiltro } from './processo.models';
import { ProcessoService } from './processo.service';

describe('ProcessoService', () => {
  let service: ProcessoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = injectService();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  function injectService(): ProcessoService {
    return TestBed.inject(ProcessoService);
  }

  it('lista com filtros e paginacao', () => {
    const filtro: ProcessoFiltro = {
      busca: 'joao',
      clienteId: null,
      tipo: 'CIVIL',
      areaJuridica: 'Direito Civil',
      tribunal: 'TJSP',
      situacao: 'EM_ANDAMENTO',
      page: 1,
      size: 10,
    };

    service.listar(filtro).subscribe();

    const req = http.expectOne((r) => r.url === `${APP_CONFIG.apiBase}/processos`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('busca')).toBe('joao');
    expect(req.request.params.get('tipo')).toBe('CIVIL');
    expect(req.request.params.get('areaJuridica')).toBe('Direito Civil');
    expect(req.request.params.get('tribunal')).toBe('TJSP');
    expect(req.request.params.get('situacao')).toBe('EM_ANDAMENTO');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('10');
    req.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('busca resumo', () => {
    service.resumo().subscribe();
    const req = http.expectOne(`${APP_CONFIG.apiBase}/processos/resumo`);
    expect(req.request.method).toBe('GET');
    req.flush({ total: 10, emAndamento: 5, suspensos: 2, encerrados: 2, urgentes: 1 });
  });
});
