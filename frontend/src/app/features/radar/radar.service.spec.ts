import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { ProcessoService } from '../processos/processo.service';
import { RadarService } from './radar.service';

describe('RadarService', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista com faixa e etapa quando informadas', () => {
    TestBed.inject(RadarService).listar({ faixa: 'TRAVADO', etapa: 'ANALISE', page: 1, size: 25 }).subscribe();
    const req = http.expectOne((r) => r.url === `${APP_CONFIG.apiBase}/radar`);
    expect(req.request.params.get('faixa')).toBe('TRAVADO');
    expect(req.request.params.get('etapa')).toBe('ANALISE');
    expect(req.request.params.get('page')).toBe('1');
    req.flush({ content: [], page: 1, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('omite faixa e etapa vazias', () => {
    TestBed.inject(RadarService).listar({ faixa: '', etapa: '', page: 0, size: 10 }).subscribe();
    const req = http.expectOne((r) => r.url === `${APP_CONFIG.apiBase}/radar`);
    expect(req.request.params.has('faixa')).toBe(false);
    expect(req.request.params.has('etapa')).toBe(false);
    req.flush({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('busca o resumo', () => {
    TestBed.inject(RadarService).resumo().subscribe();
    http.expectOne(`${APP_CONFIG.apiBase}/radar/resumo`).flush({});
  });

  it('muda a etapa do processo por PUT', () => {
    TestBed.inject(ProcessoService).mudarEtapa(7, 'PROTOCOLADO').subscribe();
    const req = http.expectOne(`${APP_CONFIG.apiBase}/processos/7/etapa`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ etapa: 'PROTOCOLADO' });
    req.flush({});
  });
});
