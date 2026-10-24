import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { APP_CONFIG } from '../../core/config/app-config';

/** Moldura padrao das telas publicas de autenticacao (login, cadastro, recuperacao de senha...). */
@Component({
  selector: 'app-auth-page',
  imports: [MatCardModule],
  template: `
    <main class="auth">
      <mat-card class="auth__card" appearance="outlined">
        <div class="auth__logo">
          <img src="/brand-mark.svg" alt="" width="32" height="32" />
          <h1 class="auth__title">{{ appName }}</h1>
        </div>
        <p class="auth__subtitle">{{ subtitle() }}</p>
        <div class="auth__content">
          <ng-content />
        </div>
      </mat-card>
    </main>
  `,
  styles: `
    /* Visual escuro herdado do login do SaaS Monitor. Os tokens abaixo recolorem os
       componentes Material apenas dentro das telas de autenticacao. */
    .auth {
      --auth-bg: #0b111b;
      --auth-card: #151c2c;
      --auth-border: #1e2a3a;
      --auth-text: #f1f5f9;
      --auth-muted: #64748b;
      --auth-accent: #3b82f6;

      color-scheme: dark;
      font-family: Inter, Roboto, sans-serif;
      --mat-sys-surface: var(--auth-card);
      --mat-sys-on-surface: var(--auth-text);
      --mat-sys-on-surface-variant: #94a3b8;
      --mat-sys-outline: var(--auth-border);
      --mat-sys-outline-variant: var(--auth-border);
      --mat-sys-primary: var(--auth-accent);
      --mat-sys-on-primary: #ffffff;
      --mat-sys-error-container: rgba(239, 68, 68, 0.12);
      --mat-sys-on-error-container: #f87171;
      --mat-sys-tertiary-container: rgba(34, 197, 94, 0.12);
      --mat-sys-on-tertiary-container: #4ade80;

      --mat-button-filled-container-height: 40px;
      --mat-button-filled-container-shape: 6px;
      --mat-button-filled-container-color: #3b82f6;
      --mat-button-filled-label-text-color: #ffffff;
      --mat-button-filled-label-text-font: Inter, Roboto, sans-serif;
      --mat-button-filled-label-text-size: 13px;
      --mat-button-filled-label-text-weight: 600;

      min-height: 100dvh;
      display: grid;
      place-items: center;
      padding: 24px;
      box-sizing: border-box;
      background: var(--auth-bg);
      color: var(--auth-text);
    }
    .auth__card {
      width: 100%;
      max-width: 380px;
      padding: 32px 28px;
      background: var(--auth-card);
      border: 1px solid var(--auth-border);
      border-radius: 12px;
      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
    }
    .auth__logo {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-bottom: 6px;
    }
    .auth__title {
      margin: 0;
      font-size: 18px;
      font-weight: 700;
      color: var(--auth-text);
    }
    .auth__content ::ng-deep .form-stack {
      margin-top: 0;
    }
    .auth__subtitle {
      margin: 0 0 20px;
      font-size: 12px;
      color: var(--auth-muted);
    }
  `,
})
export class AuthPage {
  readonly subtitle = input.required<string>();
  protected readonly appName = APP_CONFIG.name;
}
