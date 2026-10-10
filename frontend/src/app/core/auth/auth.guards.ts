import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Protege a navegacao (UX). A seguranca real e do backend: todo endpoint valida o token e a sessao.
 * A sessao ja foi restaurada pelo cookie de refresh na inicializacao do app (restoreSession).
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.hasSession()) return true;
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Impede usuario ja logado de ver as telas de login/cadastro. */
export const guestGuard: CanActivateFn = () => {
  return inject(AuthService).hasSession() ? inject(Router).createUrlTree(['/']) : true;
};
