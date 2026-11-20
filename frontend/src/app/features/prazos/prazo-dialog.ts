import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { apiErrorMessage } from '../../core/auth/api-error';
import { Responsavel, ResponsavelService } from '../../core/usuarios/responsavel.service';
import { Processo } from '../processos/processo.models';
import { ProcessoPicker, ProcessoPickerValue, processoEscolhido } from '../processos/processo-picker';
import { Prazo, TipoPrazo } from './prazo.models';
import { PrazoService } from './prazo.service';

/** Dados do diálogo: sem `prazo` cria um novo; com `prazo` edita (o processo não muda). `processo` pré-seleciona. */
export interface PrazoDialogData {
  prazo?: Prazo;
  processo?: Processo;
}

@Component({
  selector: 'app-prazo-dialog',
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    ProcessoPicker,
  ],
  template: `
    <h2 mat-dialog-title>{{ edicao ? 'Editar prazo' : 'Novo prazo' }}</h2>
    @if (carregando()) {
      <mat-progress-bar mode="indeterminate" />
    }
    <mat-dialog-content>
      <form class="form-stack" [formGroup]="form" (ngSubmit)="salvar()" id="prazo-form">
        @if (edicao) {
          <p class="processo-fixo">
            <strong>Processo:</strong> {{ data.prazo!.processoNumero }} — {{ data.prazo!.clienteNome }}
          </p>
        } @else {
          <app-processo-picker [control]="processo" />
        }

        <mat-form-field appearance="outline">
          <mat-label>Tipo de prazo</mat-label>
          <mat-select formControlName="tipo" required>
            @for (grupo of grupos(); track grupo.rotulo) {
              <mat-optgroup [label]="grupo.rotulo">
                @for (t of grupo.tipos; track t.codigo) {
                  <mat-option [value]="t.codigo">{{ t.rotulo }}</mat-option>
                }
              </mat-optgroup>
            }
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Descrição {{ form.controls.tipo.value === 'OUTRO' ? '' : '(opcional)' }}</mat-label>
          <input matInput formControlName="descricao" maxlength="300" />
          @if (form.controls.tipo.value === 'OUTRO') {
            <mat-hint>Obrigatória quando o tipo é "Outro"</mat-hint>
          }
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Data de vencimento</mat-label>
          <input matInput type="date" formControlName="dataVencimento" required />
        </mat-form-field>

        <mat-form-field appearance="outline">
          <mat-label>Responsável (opcional)</mat-label>
          <mat-select formControlName="responsavelId">
            <mat-option [value]="null">Sem responsável</mat-option>
            @for (r of responsaveis(); track r.id) {
              <mat-option [value]="r.id">{{ r.nome }}</mat-option>
            }
          </mat-select>
        </mat-form-field>

        @if (erro()) {
          <p class="form-error" role="alert">{{ erro() }}</p>
        }
      </form>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" mat-dialog-close>Cancelar</button>
      <button mat-flat-button type="submit" form="prazo-form" [disabled]="!podeSalvar() || salvando()">Salvar</button>
    </mat-dialog-actions>
  `,
  styles: `
    .processo-fixo {
      margin: 0;
    }
    mat-dialog-content {
      min-width: min(480px, 85vw);
    }
  `,
})
export class PrazoDialog implements OnInit {
  protected readonly data: PrazoDialogData = inject(MAT_DIALOG_DATA, { optional: true }) ?? {};
  private readonly dialogRef = inject(MatDialogRef<PrazoDialog, Prazo>);
  private readonly service = inject(PrazoService);
  private readonly responsaveisService = inject(ResponsavelService);

  protected readonly edicao = !!this.data.prazo;
  protected readonly tipos = signal<TipoPrazo[]>([]);
  protected readonly responsaveis = signal<Responsavel[]>([]);
  protected readonly carregando = signal(true);
  protected readonly salvando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly processo = new FormControl<ProcessoPickerValue>(this.data.processo ?? null);
  protected readonly form = new FormGroup({
    tipo: new FormControl<string | null>(this.data.prazo?.tipo ?? null, Validators.required),
    descricao: new FormControl<string>(this.data.prazo?.descricao ?? '', { nonNullable: true }),
    dataVencimento: new FormControl<string>(this.data.prazo?.dataVencimento ?? '', {
      nonNullable: true,
      validators: Validators.required,
    }),
    responsavelId: new FormControl<number | null>(this.data.prazo?.responsavelId ?? null),
  });

  /** Tipos do catálogo agrupados por categoria, na ordem em que o servidor os devolve. */
  protected readonly grupos = computed(() => {
    const porCategoria = new Map<string, TipoPrazo[]>();
    for (const t of this.tipos()) porCategoria.set(t.categoriaRotulo, [...(porCategoria.get(t.categoriaRotulo) ?? []), t]);
    return [...porCategoria].map(([rotulo, tipos]) => ({ rotulo, tipos }));
  });

  ngOnInit(): void {
    this.service.tipos().subscribe({
      next: (t) => {
        this.tipos.set(t);
        this.carregando.set(false);
      },
      error: (err) => {
        this.erro.set(apiErrorMessage(err));
        this.carregando.set(false);
      },
    });
    this.responsaveisService.listar().subscribe({ next: (r) => this.responsaveis.set(r), error: () => undefined });
  }

  protected podeSalvar(): boolean {
    const processoOk = this.edicao || processoEscolhido(this.processo.value);
    const outroSemDescricao = this.form.controls.tipo.value === 'OUTRO' && !this.form.controls.descricao.value.trim();
    return processoOk && this.form.valid && !outroSemDescricao;
  }

  protected salvar(): void {
    if (!this.podeSalvar()) return;
    const v = this.form.getRawValue();
    const escolhido = this.processo.value;
    const processoId = this.edicao ? this.data.prazo!.processoId : processoEscolhido(escolhido) ? escolhido.id : 0;
    const body = {
      processoId,
      tipo: v.tipo!,
      descricao: v.descricao.trim() || null,
      dataVencimento: v.dataVencimento,
      responsavelId: v.responsavelId,
    };
    this.salvando.set(true);
    this.erro.set(null);
    const chamada = this.edicao ? this.service.atualizar(this.data.prazo!.id, body) : this.service.criar(body);
    chamada.subscribe({
      next: (prazo) => this.dialogRef.close(prazo),
      error: (err) => {
        this.erro.set(apiErrorMessage(err));
        this.salvando.set(false);
      },
    });
  }
}
