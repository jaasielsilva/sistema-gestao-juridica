import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { formatCnj, formatDocumento } from '../../shared/br-format';
import { apiErrorMessage } from '../../core/auth/api-error';
import { FREQUENCIAS_MONITORAMENTO, FrequenciaMonitoramento, Movimentacao } from '../integracoes/escavador/escavador.models';
import { EscavadorService } from '../integracoes/escavador/escavador.service';
import { Processo, SITUACOES_PROCESSO, TIPOS_PROCESSO } from './processo.models';

@Component({
  selector: 'app-processo-detalhe-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, MatSelectModule, FormsModule, RouterLink],
  templateUrl: './processo-detalhe-dialog.html',
  styleUrl: './processo-detalhe-dialog.scss',
})
export class ProcessoDetalheDialog implements OnInit {
  protected readonly data: Processo = inject(MAT_DIALOG_DATA);
  protected readonly dialogRef = inject(MatDialogRef<ProcessoDetalheDialog>);
  private readonly escavador = inject(EscavadorService);

  protected readonly frequencias = FREQUENCIAS_MONITORAMENTO;
  protected readonly frequenciaSelecionada = signal<FrequenciaMonitoramento>(this.data.frequenciaMonitoramento ?? 'MENSAL');
  protected readonly monitoramentoBusy = signal(false);
  protected readonly monitoramentoErro = signal<string | null>(null);
  protected readonly movimentacoes = signal<Movimentacao[]>([]);
  protected readonly movimentacoesCarregando = signal(true);

  ngOnInit(): void {
    this.escavador.listarMovimentacoes(this.data.id).subscribe({
      next: (p) => {
        this.movimentacoes.set(p.content);
        this.movimentacoesCarregando.set(false);
      },
      error: () => this.movimentacoesCarregando.set(false),
    });
  }

  protected ativarMonitoramento(): void {
    this.monitoramentoErro.set(null);
    this.monitoramentoBusy.set(true);
    this.escavador.ativarMonitoramento(this.data.id, this.frequenciaSelecionada()).subscribe({
      next: () => {
        this.data.frequenciaMonitoramento = this.frequenciaSelecionada();
        this.monitoramentoBusy.set(false);
      },
      error: (err) => {
        this.monitoramentoErro.set(apiErrorMessage(err));
        this.monitoramentoBusy.set(false);
      },
    });
  }

  protected desativarMonitoramento(): void {
    this.monitoramentoErro.set(null);
    this.monitoramentoBusy.set(true);
    this.escavador.desativarMonitoramento(this.data.id).subscribe({
      next: () => {
        this.data.frequenciaMonitoramento = null;
        this.monitoramentoBusy.set(false);
      },
      error: (err) => {
        this.monitoramentoErro.set(apiErrorMessage(err));
        this.monitoramentoBusy.set(false);
      },
    });
  }

  protected formatDataHora(iso: string): string {
    return new Date(iso).toLocaleString('pt-BR');
  }

  protected get numeroFormatado(): string {
    return formatCnj(this.data.numero);
  }

  protected get tipoLabel(): string {
    return TIPOS_PROCESSO.find((t) => t.value === this.data.tipo)?.label ?? this.data.tipo;
  }

  protected get situacaoInfo(): { label: string; cssClass: string } {
    return (
      SITUACOES_PROCESSO.find((s) => s.value === this.data.situacao) ?? {
        label: this.data.situacao,
        cssClass: 'badge--default',
      }
    );
  }

  protected formatDoc(): string {
    return formatDocumento(this.data.clienteTipo, this.data.clienteDocumento);
  }

  protected formatData(iso: string | null): string {
    if (!iso) return '—';
    const parts = iso.split('-');
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return iso;
  }

  protected copiarNumero(): void {
    void navigator.clipboard.writeText(this.numeroFormatado);
  }
}
