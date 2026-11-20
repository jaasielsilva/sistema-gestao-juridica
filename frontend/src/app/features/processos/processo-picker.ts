import { Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { catchError, debounceTime, distinctUntilChanged, filter, of, switchMap } from 'rxjs';
import { formatCnj } from '../../shared/br-format';
import { Processo } from './processo.models';
import { ProcessoService } from './processo.service';

/** O mínimo que o seletor precisa saber de um processo (um `Processo` completo também serve). */
export interface ProcessoOpcao {
  id: number;
  numero: string;
  clienteNome: string;
}

/** Valor do seletor: o processo escolhido, o texto digitado (ainda sem escolha) ou nada. */
export type ProcessoPickerValue = ProcessoOpcao | string | null;

/** True quando o valor é um processo escolhido na lista (e não só texto digitado). */
export function processoEscolhido(v: ProcessoPickerValue): v is ProcessoOpcao {
  return v !== null && typeof v === 'object';
}

/**
 * Campo com busca de processo (por número, cliente ou advogado; a partir de 3 caracteres).
 * O pai cria o FormControl e decide se é obrigatório: o valor só é um processo válido quando for um objeto
 * (use `typeof control.value === 'object'`).
 */
@Component({
  selector: 'app-processo-picker',
  imports: [ReactiveFormsModule, MatAutocompleteModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline" class="picker">
      <mat-label>{{ label() }}</mat-label>
      <input
        matInput
        type="text"
        [formControl]="control()"
        [matAutocomplete]="auto"
        placeholder="Digite o número, cliente ou advogado"
        autocomplete="off"
      />
      <mat-autocomplete #auto="matAutocomplete" [displayWith]="exibir">
        @for (p of opcoes(); track p.id) {
          <mat-option [value]="p">
            {{ cnj(p.numero) }} — {{ p.clienteNome }}
          </mat-option>
        }
        @if (!opcoes().length && buscou()) {
          <mat-option disabled>Nenhum processo encontrado</mat-option>
        }
      </mat-autocomplete>
      <mat-hint>Digite ao menos 3 caracteres e escolha na lista</mat-hint>
    </mat-form-field>
  `,
  styles: `
    .picker {
      width: 100%;
    }
  `,
})
export class ProcessoPicker implements OnInit {
  readonly control = input.required<FormControl<ProcessoPickerValue>>();
  readonly label = input('Processo');

  private readonly service = inject(ProcessoService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly opcoes = signal<Processo[]>([]);
  protected readonly buscou = signal(false);

  protected readonly cnj = formatCnj;

  protected readonly exibir = (v: ProcessoPickerValue): string =>
    v && typeof v === 'object' ? `${formatCnj(v.numero)} — ${v.clienteNome}` : '';

  ngOnInit(): void {
    this.control()
      .valueChanges.pipe(
        filter((v): v is string => typeof v === 'string'),
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((texto) => {
          const busca = texto.trim();
          if (busca.length < 3) {
            this.opcoes.set([]);
            this.buscou.set(false);
            return of(null);
          }
          return this.service
            .listar({ busca, tipo: '', areaJuridica: '', tribunal: '', situacao: '', page: 0, size: 8 })
            .pipe(catchError(() => of(null)));
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((pagina) => {
        if (pagina) {
          this.opcoes.set(pagina.content);
          this.buscou.set(true);
        }
      });
  }
}
