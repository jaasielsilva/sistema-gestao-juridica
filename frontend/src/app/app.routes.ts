import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth/auth.guards';
import { Shell } from './layout/shell/shell';

export const routes: Routes = [
  // ---- Telas publicas de autenticacao ----
  {
    path: 'login',
    title: 'Entrar',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'cadastro',
    title: 'Criar conta',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'esqueci-senha',
    title: 'Esqueci minha senha',
    loadComponent: () => import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
  },
  {
    path: 'redefinir-senha',
    title: 'Redefinir senha',
    loadComponent: () => import('./features/auth/reset-password/reset-password').then((m) => m.ResetPassword),
  },
  {
    path: 'verificar-email',
    title: 'Confirmar e-mail',
    loadComponent: () => import('./features/auth/verify-email/verify-email').then((m) => m.VerifyEmail),
  },

  // ---- Area logada: layout com cabecalho, menu lateral e rodape. Novas telas entram como children. ----
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      {
        path: 'inicio',
        title: 'Início',
        loadComponent: () => import('./features/home/home').then((m) => m.Home),
      },
      {
        path: 'clientes',
        title: 'Clientes',
        loadComponent: () => import('./features/clientes/clientes-list').then((m) => m.ClientesList),
      },
      {
        path: 'clientes/novo',
        title: 'Novo cliente',
        loadComponent: () => import('./features/clientes/cliente-form').then((m) => m.ClienteForm),
      },
      {
        path: 'clientes/:id/editar',
        title: 'Editar cliente',
        loadComponent: () => import('./features/clientes/cliente-form').then((m) => m.ClienteForm),
      },
      {
        path: 'processos',
        title: 'Processos',
        loadComponent: () => import('./features/processos/processos-list').then((m) => m.ProcessosList),
      },
      {
        path: 'processos/novo',
        title: 'Novo Processo',
        loadComponent: () => import('./features/processos/processo-form').then((m) => m.ProcessoForm),
      },
      {
        path: 'processos/:id/editar',
        title: 'Editar Processo',
        loadComponent: () => import('./features/processos/processo-form').then((m) => m.ProcessoForm),
      },
      {
        path: 'seguranca',
        title: 'Segurança',
        loadComponent: () => import('./features/account/security/security').then((m) => m.Security),
      },
      {
        path: '**',
        title: 'Página não encontrada',
        loadComponent: () => import('./features/not-found/not-found').then((m) => m.NotFound),
      },
    ],
  },
];
