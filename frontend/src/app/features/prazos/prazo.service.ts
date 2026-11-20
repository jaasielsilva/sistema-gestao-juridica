import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../../core/config/app-config';
import { Page } from '../clientes/cliente.models';
import { Prazo, PrazoFiltro, PrazoRequest, PrazoResumo, TipoPrazo } from './prazo.models';

const BASE = `${APP_CONFIG.apiBase}/prazos`;

@Injectable({ providedIn: 'root' })
export class PrazoService {
  private readonly http = inject(HttpClient);

  listar(f: PrazoFiltro): Observable<Page<Prazo>> {
    let params = new HttpParams().set('page', f.page).set('size', f.size);
    if (f.situacao) params = params.set('situacao', f.situacao);
    if (f.dias !== '') params = params.set('dias', f.dias);
    if (f.categoria) params = params.set('categoria', f.categoria);
    if (f.responsavelId !== '') params = params.set('responsavelId', f.responsavelId);
    if (f.processoId) params = params.set('processoId', f.processoId);
    if (f.busca && f.busca.trim()) params = params.set('busca', f.busca.trim());
    return this.http.get<Page<Prazo>>(BASE, { params });
  }

  resumo(): Observable<PrazoResumo> {
    return this.http.get<PrazoResumo>(`${BASE}/resumo`);
  }

  tipos(): Observable<TipoPrazo[]> {
    return this.http.get<TipoPrazo[]>(`${BASE}/tipos`);
  }

  criar(body: PrazoRequest): Observable<Prazo> {
    return this.http.post<Prazo>(BASE, body);
  }

  atualizar(id: number, body: PrazoRequest): Observable<Prazo> {
    return this.http.put<Prazo>(`${BASE}/${id}`, body);
  }

  cumprir(id: number): Observable<Prazo> {
    return this.http.post<Prazo>(`${BASE}/${id}/cumprir`, {});
  }

  reabrir(id: number): Observable<Prazo> {
    return this.http.post<Prazo>(`${BASE}/${id}/reabrir`, {});
  }

  excluir(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/${id}`);
  }
}
