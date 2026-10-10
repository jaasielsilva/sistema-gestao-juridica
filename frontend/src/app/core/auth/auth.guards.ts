import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Protege a navegacao (UX). A seguranca real e do backend: todo endpoint valida o token.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.hasValidSession()) return true;
  return inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Impede usuario ja logado de ver a tela de login. */
export const guestGuard: CanActivateFn = () => {
  return inject(AuthService).hasValidSession() ? inject(Router).createUrlTree(['/']) : true;
};
