import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../../core/config/app-config';
import { Page } from '../clientes/cliente.models';
import { Processo, ProcessoFiltro, ProcessoRequest, ProcessoResumo } from './processo.models';

const BASE = `${APP_CONFIG.apiBase}/processos`;

@Injectable({ providedIn: 'root' })
export class ProcessoService {
  private readonly http = inject(HttpClient);

  listar(f: ProcessoFiltro): Observable<Page<Processo>> {
    let params = new HttpParams().set('page', f.page).set('size', f.size);
    if (f.busca && f.busca.trim()) params = params.set('busca', f.busca.trim());
    if (f.clienteId) params = params.set('clienteId', f.clienteId);
    if (f.tipo) params = params.set('tipo', f.tipo);
    if (f.areaJuridica && f.areaJuridica.trim()) params = params.set('areaJuridica', f.areaJuridica.trim());
    if (f.tribunal && f.tribunal.trim()) params = params.set('tribunal', f.tribunal.trim());
    if (f.situacao) params = params.set('situacao', f.situacao);
    return this.http.get<Page<Processo>>(BASE, { params });
  }

  resumo(): Observable<ProcessoResumo> {
    return this.http.get<ProcessoResumo>(`${BASE}/resumo`);
  }

  buscar(id: number): Observable<Processo> {
    return this.http.get<Processo>(`${BASE}/${id}`);
  }

  criar(body: ProcessoRequest): Observable<Processo> {
    return this.http.post<Processo>(BASE, body);
  }

  atualizar(id: number, body: ProcessoRequest): Observable<Processo> {
    return this.http.put<Processo>(`${BASE}/${id}`, body);
  }
}
