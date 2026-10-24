import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { formatCnj, formatDocumento } from '../../shared/br-format';
import { Processo, SITUACOES_PROCESSO, TIPOS_PROCESSO } from './processo.models';

@Component({
  selector: 'app-processo-detalhe-dialog',
  imports: [MatDialogModule, MatButtonModule, MatIconModule, RouterLink],
  templateUrl: './processo-detalhe-dialog.html',
  styleUrl: './processo-detalhe-dialog.scss',
})
export class ProcessoDetalheDialog {
  protected readonly data: Processo = inject(MAT_DIALOG_DATA);
  protected readonly dialogRef = inject(MatDialogRef<ProcessoDetalheDialog>);

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
