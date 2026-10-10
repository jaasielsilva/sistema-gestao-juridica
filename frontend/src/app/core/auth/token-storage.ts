import { Injectable } from '@angular/core';

export interface Session {
  token: string;
  /** Epoch em milissegundos. */
  expiresAt: number;
}

const KEY = 'app.session';

/**
 * Guarda o token em sessionStorage (some ao fechar a aba).
 * Trade-off: qualquer XSS conseguiria le-lo. Mitigacoes: CSP no nginx, sem HTML dinamico,
 * token curto. Para risco maior, migrar para cookie HttpOnly (exige mudanca no backend).
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  read(): Session | null {
    try {
      const raw = sessionStorage.getItem(KEY);
      if (!raw) return null;
      const s = JSON.parse(raw) as Session;
      return typeof s.token === 'string' && typeof s.expiresAt === 'number' ? s : null;
    } catch {
      return null;
    }
  }

  write(session: Session): void {
    try {
      sessionStorage.setItem(KEY, JSON.stringify(session));
    } catch {
      /* armazenamento indisponivel: a sessao vale so ate recarregar a pagina */
    }
  }

  clear(): void {
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      /* ignorado */
    }
  }
}
