import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { apiErrorMessage } from '../../../core/auth/api-error';
import { EscavadorConfiguracao } from './escavador.models';
import { EscavadorService } from './escavador.service';

@Component({
  selector: 'app-escavador-configuracao',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './escavador-configuracao.html',
  styleUrl: './escavador-configuracao.scss',
})
export class EscavadorConfiguracaoPage {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(EscavadorService);

  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly sucesso = signal<string | null>(null);
  protected readonly config = signal<EscavadorConfiguracao | null>(null);

  protected readonly form = this.fb.nonNullable.group({
    token: ['', [Validators.required]],
    callbackSecret: ['', [Validators.required]],
    saldoLimiteReais: [50, [Validators.required, Validators.min(0)]],
  });

  constructor() {
    this.service.obterConfiguracao().subscribe({
      next: (c) => {
        this.config.set(c);
        if (c.configurado) {
          this.form.patchValue({ saldoLimiteReais: c.saldoLimiteCentavos / 100 });
        }
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.erro.set(null);
    this.sucesso.set(null);
    this.salvando.set(true);
    const v = this.form.getRawValue();
    this.service
      .salvarConfiguracao({
        token: v.token,
        callbackSecret: v.callbackSecret,
        saldoLimiteCentavos: Math.round(v.saldoLimiteReais * 100),
      })
      .subscribe({
        next: (c) => {
          this.config.set(c);
          this.sucesso.set('Configuração salva. Copie a URL de callback abaixo e cole no painel do Escavador.');
          this.form.patchValue({ token: '', callbackSecret: '' });
          this.salvando.set(false);
        },
        error: (err) => {
          this.erro.set(apiErrorMessage(err));
          this.salvando.set(false);
        },
      });
  }

  protected copiarUrlCallback(): void {
    const url = this.config()?.urlCallback;
    if (url) {
      void navigator.clipboard.writeText(url);
    }
  }

  protected formatarReais(centavos: number): string {
    return (centavos / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }
}
