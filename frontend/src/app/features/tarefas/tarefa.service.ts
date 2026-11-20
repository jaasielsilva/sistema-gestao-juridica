import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../../core/config/app-config';
import { StatusTarefa, Tarefa, TarefaRequest } from './tarefa.models';

const BASE = `${APP_CONFIG.apiBase}/tarefas`;

@Injectable({ providedIn: 'root' })
export class TarefaService {
  private readonly http = inject(HttpClient);

  quadro(filtro: { responsavelId?: number | null; processoId?: number | null; busca?: string } = {}): Observable<Tarefa[]> {
    let params = new HttpParams();
    if (filtro.busca && filtro.busca.trim()) params = params.set('busca', filtro.busca.trim());
    if (filtro.responsavelId) params = params.set('responsavelId', filtro.responsavelId);
    if (filtro.processoId) params = params.set('processoId', filtro.processoId);
    return this.http.get<Tarefa[]>(BASE, { params });
  }

  criar(body: TarefaRequest): Observable<Tarefa> {
    return this.http.post<Tarefa>(BASE, body);
  }

  atualizar(id: number, body: TarefaRequest): Observable<Tarefa> {
    return this.http.put<Tarefa>(`${BASE}/${id}`, body);
  }

  mover(id: number, status: StatusTarefa, posicao: number): Observable<Tarefa> {
    return this.http.put<Tarefa>(`${BASE}/${id}/mover`, { status, posicao });
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/${id}`);
  }
}
