import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { RouterLink } from '@angular/router';
import { formatCnj, formatDocumento } from '../../shared/br-format';
import { apiErrorMessage } from '../../core/auth/api-error';
import { FREQUENCIAS_MONITORAMENTO, FrequenciaMonitoramento, Movimentacao } from '../integracoes/escavador/escavador.models';
import { EscavadorService } from '../integracoes/escavador/escavador.service';
import { PrazoDialog } from '../prazos/prazo-dialog';
import { Prazo, SITUACOES_PRAZO, dataBr } from '../prazos/prazo.models';
import { PrazoService } from '../prazos/prazo.service';
import { ETAPAS_PROCESSO, EtapaProcesso, Processo, SITUACOES_PROCESSO, TIPOS_PROCESSO } from './processo.models';
import { ProcessoService } from './processo.service';

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
  private readonly prazoService = inject(PrazoService);
  private readonly processoService = inject(ProcessoService);
  private readonly dialog = inject(MatDialog);

  protected readonly etapas = ETAPAS_PROCESSO;
  protected readonly prazos = signal<Prazo[]>([]);
  protected readonly prazosCarregando = signal(true);
  protected readonly prazosErro = signal<string | null>(null);
  protected readonly dataBr = dataBr;

  protected readonly frequencias = FREQUENCIAS_MONITORAMENTO;
  protected readonly frequenciaSelecionada = signal<FrequenciaMonitoramento>(this.data.frequenciaMonitoramento ?? 'MENSAL');
  protected readonly monitoramentoBusy = signal(false);
  protected readonly monitoramentoErro = signal<string | null>(null);
  protected readonly movimentacoes = signal<Movimentacao[]>([]);
  protected readonly movimentacoesCarregando = signal(true);

  ngOnInit(): void {
    this.carregarPrazos();
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

  protected rotuloSituacaoPrazo(s: string): string {
    return SITUACOES_PRAZO.find((x) => x.value === s)?.label ?? s;
  }

  protected cssSituacaoPrazo(s: string): string {
    return SITUACOES_PRAZO.find((x) => x.value === s)?.cssClass ?? '';
  }

  protected novoPrazo(): void {
    this.dialog.open(PrazoDialog, { data: { processo: this.data } }).afterClosed().subscribe((salvo) => {
      if (salvo) this.atualizarPrazos();
    });
  }

  protected cumprirPrazo(prazo: Prazo): void {
    this.prazoService.cumprir(prazo.id).subscribe({
      next: () => this.atualizarPrazos(),
      error: (err) => this.prazosErro.set(apiErrorMessage(err)),
    });
  }

  protected reabrirPrazo(prazo: Prazo): void {
    this.prazoService.reabrir(prazo.id).subscribe({
      next: () => this.atualizarPrazos(),
      error: (err) => this.prazosErro.set(apiErrorMessage(err)),
    });
  }

  protected mudarEtapa(etapa: EtapaProcesso): void {
    if (etapa === this.data.etapa) return;
    this.prazosErro.set(null);
    this.processoService.mudarEtapa(this.data.id, etapa).subscribe({
      next: (p) => {
        this.data.etapa = p.etapa;
        this.data.etapaRotulo = p.etapaRotulo;
        this.data.etapaAtualizadaEm = p.etapaAtualizadaEm;
      },
      error: (err) => this.prazosErro.set(apiErrorMessage(err)),
    });
  }

  /** Recarrega os prazos e o processo (o próximo prazo mostrado acima muda quando um prazo é criado ou baixado). */
  private atualizarPrazos(): void {
    this.carregarPrazos();
    this.processoService.buscar(this.data.id).subscribe({
      next: (p) => {
        this.data.proximoPrazo = p.proximoPrazo;
        this.data.proximoPrazoDescricao = p.proximoPrazoDescricao;
      },
      error: () => undefined,
    });
  }

  private carregarPrazos(): void {
    this.prazosErro.set(null);
    this.prazoService
      .listar({ situacao: '', dias: '', categoria: '', responsavelId: '', processoId: this.data.id, page: 0, size: 20 })
      .subscribe({
        next: (pagina) => {
          this.prazos.set(pagina.content);
          this.prazosCarregando.set(false);
        },
        error: (err) => {
          this.prazosErro.set(apiErrorMessage(err));
          this.prazosCarregando.set(false);
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
