import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { formatDocumento, formatTelefone } from '../../shared/br-format';
import { Cliente } from './cliente.models';

@Component({
  selector: 'app-cliente-detalhe-dialog',
  imports: [MatButtonModule, MatDialogModule, MatIconModule],
  templateUrl: './cliente-detalhe-dialog.html',
  styleUrl: './cliente-detalhe-dialog.scss',
})
export class ClienteDetalheDialog {
  protected readonly cliente = inject<Cliente>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject(MatDialogRef<ClienteDetalheDialog>);

  protected documento(): string {
    return formatDocumento(this.cliente.tipo, this.cliente.documento);
  }

  protected telefone(): string {
    return this.cliente.telefone ? formatTelefone(this.cliente.telefone) : '—';
  }

  protected fechar(): void {
    this.dialogRef.close();
  }
}
