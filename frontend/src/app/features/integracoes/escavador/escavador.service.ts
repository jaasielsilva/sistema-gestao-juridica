import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../../../core/config/app-config';
import { Page } from '../../clientes/cliente.models';
import {
  EscavadorConfiguracao,
  EscavadorConfiguracaoRequest,
  FrequenciaMonitoramento,
  Movimentacao,
} from './escavador.models';

const BASE = `${APP_CONFIG.apiBase}/integracoes/escavador`;

@Injectable({ providedIn: 'root' })
export class EscavadorService {
  private readonly http = inject(HttpClient);

  obterConfiguracao(): Observable<EscavadorConfiguracao> {
    return this.http.get<EscavadorConfiguracao>(`${BASE}/configuracao`);
  }

  salvarConfiguracao(body: EscavadorConfiguracaoRequest): Observable<EscavadorConfiguracao> {
    return this.http.put<EscavadorConfiguracao>(`${BASE}/configuracao`, body);
  }

  ativarMonitoramento(processoId: number, frequencia: FrequenciaMonitoramento): Observable<void> {
    return this.http.post<void>(`${BASE}/processos/${processoId}/monitoramento`, { frequencia });
  }

  desativarMonitoramento(processoId: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/processos/${processoId}/monitoramento`);
  }

  listarMovimentacoes(processoId: number, page = 0, size = 20): Observable<Page<Movimentacao>> {
    return this.http.get<Page<Movimentacao>>(`${BASE}/processos/${processoId}/movimentacoes`, {
      params: { page, size },
    });
  }
}
