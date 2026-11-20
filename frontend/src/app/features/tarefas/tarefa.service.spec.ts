import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { APP_CONFIG } from '../../core/config/app-config';
import { Tarefa, agruparPorColuna } from './tarefa.models';
import { TarefaService } from './tarefa.service';

function tarefa(id: number, status: Tarefa['status'], extra: Partial<Tarefa> = {}): Tarefa {
  return {
    id,
    titulo: `T${id}`,
    descricao: null,
    status,
    prioridade: 'MEDIA',
    prazo: null,
    atrasada: false,
    processoId: null,
    processoNumero: null,
    clienteNome: null,
    responsavelId: null,
    responsavelNome: null,
    ordem: 0,
    criadoPorId: 1,
    concluidaEm: null,
    criadoEm: '2026-11-20T10:00:00Z',
    ...extra,
  };
}

describe('TarefaService', () => {
  let service: TarefaService;
  let http: HttpTestingController;
  const base = `${APP_CONFIG.apiBase}/tarefas`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(TarefaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('carrega o quadro filtrando por responsável e processo', () => {
    service.quadro({ responsavelId: 3, processoId: 8 }).subscribe();
    const req = http.expectOne((r) => r.url === base);
    expect(req.request.params.get('responsavelId')).toBe('3');
    expect(req.request.params.get('processoId')).toBe('8');
    req.flush([]);
  });

  it('carrega o quadro sem filtros', () => {
    service.quadro().subscribe();
    const req = http.expectOne((r) => r.url === base);
    expect(req.request.params.keys().length).toBe(0);
    req.flush([]);
  });

  it('move enviando coluna e posição', () => {
    service.mover(4, 'EM_ANDAMENTO', 2).subscribe();
    const req = http.expectOne(`${base}/4/mover`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ status: 'EM_ANDAMENTO', posicao: 2 });
    req.flush({});
  });

  it('cria, atualiza e exclui', () => {
    service.criar({ titulo: 'X', prioridade: 'ALTA' }).subscribe();
    expect(http.expectOne(base).request.method).toBe('POST');
    http.match(base).forEach((r) => r.flush({}));

    service.atualizar(2, { titulo: 'Y', prioridade: 'BAIXA' }).subscribe();
    const put = http.expectOne(`${base}/2`);
    expect(put.request.method).toBe('PUT');
    put.flush({});

    service.excluir(2).subscribe();
    const del = http.expectOne(`${base}/2`);
    expect(del.request.method).toBe('DELETE');
    del.flush(null);
  });
});

describe('agruparPorColuna', () => {
  it('separa por status mantendo a ordem do servidor', () => {
    const colunas = agruparPorColuna([tarefa(1, 'A_FAZER'), tarefa(2, 'EM_ANDAMENTO'), tarefa(3, 'A_FAZER')]);
    expect(colunas.A_FAZER.map((t) => t.id)).toEqual([1, 3]);
    expect(colunas.EM_ANDAMENTO.map((t) => t.id)).toEqual([2]);
    expect(colunas.AGUARDANDO).toEqual([]);
    expect(colunas.CONCLUIDA).toEqual([]);
  });

  it('mostra as concluídas da mais recente para a mais antiga', () => {
    const colunas = agruparPorColuna([
      tarefa(1, 'CONCLUIDA', { concluidaEm: '2026-11-10T10:00:00Z' }),
      tarefa(2, 'CONCLUIDA', { concluidaEm: '2026-11-19T10:00:00Z' }),
    ]);
    expect(colunas.CONCLUIDA.map((t) => t.id)).toEqual([2, 1]);
  });
});
