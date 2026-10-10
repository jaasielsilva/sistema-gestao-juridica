import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-not-found',
  imports: [MatButtonModule, RouterLink],
  template: `
    <h1 class="page-title">Página não encontrada</h1>
    <p>O endereço que você acessou não existe.</p>
    <a mat-flat-button routerLink="/inicio">Voltar ao início</a>
  `,
})
export class NotFound {}
