import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';

/** Colega do escritório que pode ser responsável por um prazo ou tarefa (só id e nome). */
export interface Responsavel {
  id: number;
  nome: string;
}

@Injectable({ providedIn: 'root' })
export class ResponsavelService {
  private readonly http = inject(HttpClient);

  listar(): Observable<Responsavel[]> {
    return this.http.get<Responsavel[]>(`${APP_CONFIG.apiBase}/usuarios/responsaveis`);
  }
}
