import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../../core/config/app-config';
import { Page } from '../clientes/cliente.models';
import { EtapaProcesso } from '../processos/processo.models';
import { RadarFaixa, RadarItem, RadarResumo } from './radar.models';

const BASE = `${APP_CONFIG.apiBase}/radar`;

@Injectable({ providedIn: 'root' })
export class RadarService {
  private readonly http = inject(HttpClient);

  listar(f: {
    faixa: RadarFaixa | '';
    etapa: EtapaProcesso | '';
    busca?: string;
    page: number;
    size: number;
  }): Observable<Page<RadarItem>> {
    let params = new HttpParams().set('page', f.page).set('size', f.size);
    if (f.busca && f.busca.trim()) params = params.set('busca', f.busca.trim());
    if (f.faixa) params = params.set('faixa', f.faixa);
    if (f.etapa) params = params.set('etapa', f.etapa);
    return this.http.get<Page<RadarItem>>(BASE, { params });
  }

  resumo(): Observable<RadarResumo> {
    return this.http.get<RadarResumo>(`${BASE}/resumo`);
  }
}
