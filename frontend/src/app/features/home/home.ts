import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { ClienteResumo } from '../clientes/cliente.models';
import { ClienteService } from '../clientes/cliente.service';

@Component({
  selector: 'app-home',
  imports: [MatButtonModule, MatIconModule, MatProgressBarModule, RouterLink],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  protected readonly auth = inject(AuthService);
  private readonly clientes = inject(ClienteService);

  protected readonly resumo = signal<ClienteResumo | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly primeiroNome = computed(
    () => this.auth.user()?.nome.trim().split(/\s+/)[0] || 'bem-vindo',
  );
  protected readonly summaryCards: { label: string; value: keyof ClienteResumo; icon: string }[] = [
    { label: 'Total de clientes', value: 'total', icon: 'groups' },
    { label: 'Ativos', value: 'ativos', icon: 'check_circle' },
    { label: 'Inativos', value: 'inativos', icon: 'person_off' },
    { label: 'Pessoas físicas', value: 'pessoasFisicas', icon: 'person' },
    { label: 'Pessoas jurídicas', value: 'pessoasJuridicas', icon: 'business' },
  ];

  constructor() {
    this.clientes.resumo().subscribe({
      next: (resumo) => {
        this.resumo.set(resumo);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
