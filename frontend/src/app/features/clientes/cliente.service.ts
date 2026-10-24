import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../../core/config/app-config';
import { Cliente, ClienteFiltro, ClienteRequest, ClienteResumo, Page } from './cliente.models';

const BASE = `${APP_CONFIG.apiBase}/clientes`;

@Injectable({ providedIn: 'root' })
export class ClienteService {
  private readonly http = inject(HttpClient);

  listar(f: ClienteFiltro): Observable<Page<Cliente>> {
    let params = new HttpParams().set('page', f.page).set('size', f.size);
    if (f.busca.trim()) params = params.set('busca', f.busca.trim());
    if (f.tipo) params = params.set('tipo', f.tipo);
    if (f.status !== 'todos') params = params.set('ativo', f.status === 'ativos');
    if (f.cidade.trim()) params = params.set('cidade', f.cidade.trim());
    if (f.uf) params = params.set('uf', f.uf);
    return this.http.get<Page<Cliente>>(BASE, { params });
  }

  resumo(): Observable<ClienteResumo> {
    return this.http.get<ClienteResumo>(`${BASE}/resumo`);
  }

  buscar(id: number): Observable<Cliente> {
    return this.http.get<Cliente>(`${BASE}/${id}`);
  }

  criar(body: ClienteRequest): Observable<Cliente> {
    return this.http.post<Cliente>(BASE, body);
  }

  atualizar(id: number, body: ClienteRequest): Observable<Cliente> {
    return this.http.put<Cliente>(`${BASE}/${id}`, body);
  }

  inativar(id: number): Observable<Cliente> {
    return this.http.post<Cliente>(`${BASE}/${id}/inativar`, {});
  }

  reativar(id: number): Observable<Cliente> {
    return this.http.post<Cliente>(`${BASE}/${id}/reativar`, {});
  }
}
