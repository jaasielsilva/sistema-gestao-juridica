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
        <mat-card-header>
          <mat-card-title class="auth__title">{{ appName }}</mat-card-title>
          <mat-card-subtitle>{{ subtitle() }}</mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
          <ng-content />
        </mat-card-content>
      </mat-card>
    </main>
  `,
  styles: `
    .auth {
      min-height: 100dvh;
      display: grid;
      place-items: center;
      padding: 16px;
      box-sizing: border-box;
    }
    .auth__card {
      width: 100%;
      max-width: 420px;
      padding: 16px 8px 8px;
    }
    .auth__title {
      font: var(--mat-sys-headline-small);
    }
  `,
})
export class AuthPage {
  readonly subtitle = input.required<string>();
  protected readonly appName = APP_CONFIG.name;
}
