import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import Swal from 'sweetalert2';
import { apiErrorMessage } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { Responsavel, ResponsavelService } from '../../core/usuarios/responsavel.service';
import { ProcessoPicker, ProcessoPickerValue, processoEscolhido } from '../processos/processo-picker';
import { PRIORIDADES_TAREFA, PrioridadeTarefa, StatusTarefa, Tarefa } from './tarefa.models';
import { TarefaService } from './tarefa.service';

/** Resultado do diálogo: a tarefa salva, ou `{ excluida: id }` quando foi apagada. */
export type TarefaDialogResult = Tarefa | { excluida: number };

/** Sem `tarefa`, cria uma nova na coluna `status` (padrão A_FAZER); com `tarefa`, edita. */
export interface TarefaDialogData {
  tarefa?: Tarefa;
  status?: StatusTarefa;
}

@Component({
  selector: 'app-tarefa-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, ProcessoPicker],
  template: `
    <h2 mat-dialog-title>{{ edicao ? 'Editar tarefa' : 'Nova tarefa' }}</h2>
    <mat-dialog-content>
      <form class="form-stack" [formGroup]="form" (ngSubmit)="salvar()" id="tarefa-form">
        <mat-form-field appearance="outline">
          <mat-label>Título</mat-label>
          <input matInput formControlName="titulo" maxlength="150" required />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Descrição (opcional)</mat-label>
          <textarea matInput formControlName="descricao" rows="3" maxlength="2000"></textarea>
        </mat-form-field>

        <div class="linha">
          <mat-form-field appearance="outline">
            <mat-label>Prioridade</mat-label>
            <mat-select formControlName="prioridade" required>
              @for (p of prioridades; track p.value) {
                <mat-option [value]="p.value">{{ p.label }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Prazo (opcional)</mat-label>
            <input matInput type="date" formControlName="prazo" />
          </mat-form-field>
        </div>

        <mat-form-field appearance="outline">
          <mat-label>Responsável (opcional)</mat-label>
          <mat-select formControlName="responsavelId">
            <mat-option [value]="null">Sem responsável</mat-option>
            @for (r of responsaveis(); track r.id) {
              <mat-option [value]="r.id">{{ r.nome }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        <app-processo-picker [control]="processo" label="Processo (opcional)" />
        @if (processoInvalido()) {
          <p class="form-error" role="alert">Escolha um processo da lista ou apague o texto.</p>
        }

        @if (erro()) {
          <p class="form-error" role="alert">{{ erro() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions>
      @if (podeExcluir()) {
        <button mat-button type="button" class="excluir" (click)="excluir()" [disabled]="salvando()">Excluir</button>
      }
      <span class="espaco"></span>
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="submit" form="tarefa-form" [disabled]="!podeSalvar() || salvando()">Salvar</button>
    </mat-dialog-actions>
  `,
  styles: `
    mat-dialog-content { min-width: min(520px, 85vw); }
    .linha { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    mat-dialog-actions { display: flex; align-items: center; padding: 8px 24px 16px; }
    .espaco { flex: 1; }
    .excluir { color: var(--mat-sys-error); }
  `,
})
export class TarefaDialog implements OnInit {
  protected readonly data: TarefaDialogData = inject(MAT_DIALOG_DATA, { optional: true }) ?? {};
  private readonly dialogRef = inject(MatDialogRef<TarefaDialog, TarefaDialogResult>);
  private readonly service = inject(TarefaService);
  private readonly responsaveisService = inject(ResponsavelService);
  private readonly auth = inject(AuthService);

  protected readonly edicao = !!this.data.tarefa;
  protected readonly prioridades = PRIORIDADES_TAREFA;
  protected readonly responsaveis = signal<Responsavel[]>([]);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly processo = new FormControl<ProcessoPickerValue>(
    this.data.tarefa?.processoId
      ? { id: this.data.tarefa.processoId, numero: this.data.tarefa.processoNumero ?? '', clienteNome: this.data.tarefa.clienteNome ?? '' }
      : null,
  );
  protected readonly form = new FormGroup({
    titulo: new FormControl<string>(this.data.tarefa?.titulo ?? '', { nonNullable: true, validators: Validators.required }),
    descricao: new FormControl<string>(this.data.tarefa?.descricao ?? '', { nonNullable: true }),
    prioridade: new FormControl<PrioridadeTarefa>(this.data.tarefa?.prioridade ?? 'MEDIA', {
      nonNullable: true,
      validators: Validators.required,
    }),
    prazo: new FormControl<string>(this.data.tarefa?.prazo ?? '', { nonNullable: true }),
    responsavelId: new FormControl<number | null>(this.data.tarefa?.responsavelId ?? null),
  });

  ngOnInit(): void {
    this.responsaveisService.listar().subscribe({ next: (r) => this.responsaveis.set(r), error: () => undefined });
  }

  /** Só quem criou a tarefa ou um ADMIN pode excluir (o servidor confere de novo). */
  protected podeExcluir(): boolean {
    const t = this.data.tarefa;
    return !!t && (this.auth.isAdmin() || t.criadoPorId === this.auth.user()?.id);
  }

  protected processoInvalido(): boolean {
    const v = this.processo.value;
    return typeof v === 'string' && v.trim().length > 0;
  }

  protected podeSalvar(): boolean {
    return this.form.valid && !!this.form.controls.titulo.value.trim() && !this.processoInvalido();
  }

  protected salvar(): void {
    if (!this.podeSalvar()) return;
    const v = this.form.getRawValue();
    const escolhido = this.processo.value;
    const body = {
      titulo: v.titulo.trim(),
      descricao: v.descricao.trim() || null,
      prioridade: v.prioridade,
      prazo: v.prazo || null,
      processoId: processoEscolhido(escolhido) ? escolhido.id : null,
      responsavelId: v.responsavelId,
      status: this.data.status ?? null,
    };
    this.salvando.set(true);
    this.erro.set(null);
    const chamada = this.edicao ? this.service.atualizar(this.data.tarefa!.id, body) : this.service.criar(body);
    chamada.subscribe({
      next: (t) => this.dialogRef.close(t),
      error: (err) => {
        this.erro.set(apiErrorMessage(err));
        this.salvando.set(false);
      },
    });
  }

  protected async excluir(): Promise<void> {
    const t = this.data.tarefa!;
    const r = await Swal.fire({
      title: 'Excluir tarefa?',
      text: `"${t.titulo}" será removida. Esta ação não pode ser desfeita.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Excluir',
      cancelButtonText: 'Cancelar',
    });
    if (!r.isConfirmed) return;
    this.salvando.set(true);
    this.service.excluir(t.id).subscribe({
      next: () => this.dialogRef.close({ excluida: t.id }),
      error: (err) => {
        this.erro.set(apiErrorMessage(err));
        this.salvando.set(false);
      },
    });
  }
}
