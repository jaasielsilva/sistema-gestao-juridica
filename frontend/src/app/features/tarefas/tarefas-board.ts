import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup, moveItemInArray, transferArrayItem } from '@angular/cdk/drag-drop';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { apiErrorMessage } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notification/notification.service';
import { Responsavel, ResponsavelService } from '../../core/usuarios/responsavel.service';
import { formatCnj } from '../../shared/br-format';
import { dataBr } from '../prazos/prazo.models';
import { TarefaDialog, TarefaDialogResult } from './tarefa-dialog';
import { COLUNAS_TAREFA, PRIORIDADES_TAREFA, StatusTarefa, Tarefa, agruparPorColuna } from './tarefa.models';
import { TarefaService } from './tarefa.service';

/** Quadro Kanban de tarefas: arraste os cartões entre as colunas; cada cartão pode ter processo e responsável. */
@Component({
  selector: 'app-tarefas-board',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    CdkDropListGroup,
    CdkDropList,
    CdkDrag,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatProgressBarModule,
  ],
  host: { class: 'fill-page' },
  templateUrl: './tarefas-board.html',
  styleUrl: './tarefas-board.scss',
})
export class TarefasBoard implements OnInit {
  private readonly service = inject(TarefaService);
  private readonly responsaveisService = inject(ResponsavelService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);

  /** Caixa de busca: título, descrição, cliente ou número do processo da tarefa. */
  protected readonly busca = new FormControl('', { nonNullable: true });
  private readonly textoBusca = signal('');

  protected readonly colunasDef = COLUNAS_TAREFA;
  protected readonly cnj = formatCnj;
  protected readonly data = dataBr;

  protected readonly tarefas = signal<Tarefa[]>([]);
  protected readonly responsaveis = signal<Responsavel[]>([]);
  protected readonly responsavelId = signal<number | null>(null);
  protected readonly soMinhas = signal(false);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);

  /** Cartões por coluna. Os arrays são mutados pelo arrastar-e-soltar do CDK e o signal é reemitido em seguida. */
  protected readonly colunas = computed(() => agruparPorColuna(this.tarefas()));

  /** Quantos filtros estão ligados (o botão "Limpar" só aparece com algum ligado). */
  protected readonly filtrosAtivos = computed(
    () => (this.soMinhas() ? 1 : 0) + (this.responsavelId() !== null ? 1 : 0) + (this.textoBusca() ? 1 : 0),
  );

  ngOnInit(): void {
    this.busca.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((texto) => {
        this.textoBusca.set(texto.trim());
        this.carregar();
      });
    this.responsaveisService.listar().subscribe({ next: (r) => this.responsaveis.set(r), error: () => undefined });
    this.carregar();
  }

  protected rotuloPrioridade(p: string): string {
    return PRIORIDADES_TAREFA.find((x) => x.value === p)?.label ?? p;
  }

  protected alternarMinhas(ligado: boolean): void {
    this.soMinhas.set(ligado);
    this.carregar();
  }

  protected filtrarResponsavel(id: number | null): void {
    this.responsavelId.set(id);
    this.carregar();
  }

  protected limparFiltros(): void {
    this.busca.setValue('', { emitEvent: false });
    this.textoBusca.set('');
    this.soMinhas.set(false);
    this.responsavelId.set(null);
    this.carregar();
  }

  protected novaTarefa(status: StatusTarefa): void {
    this.dialog.open(TarefaDialog, { data: { status } }).afterClosed().subscribe((r?: TarefaDialogResult) => {
      if (r) {
        this.notificacao.success('Tarefa criada.');
        this.carregar();
      }
    });
  }

  protected editar(tarefa: Tarefa): void {
    this.dialog.open(TarefaDialog, { data: { tarefa } }).afterClosed().subscribe((r?: TarefaDialogResult) => {
      if (!r) return;
      this.notificacao.success('excluida' in r ? 'Tarefa excluída.' : 'Tarefa atualizada.');
      this.carregar();
    });
  }

  /** Move o cartão na tela na hora (otimista) e confirma no servidor; se falhar, recarrega o quadro real. */
  protected soltar(evento: CdkDragDrop<Tarefa[]>, destino: StatusTarefa): void {
    const mesma = evento.previousContainer === evento.container;
    if (mesma && evento.previousIndex === evento.currentIndex) return;

    const tarefa: Tarefa = evento.item.data;
    if (mesma) {
      moveItemInArray(evento.container.data, evento.previousIndex, evento.currentIndex);
    } else {
      transferArrayItem(evento.previousContainer.data, evento.container.data, evento.previousIndex, evento.currentIndex);
    }
    tarefa.status = destino;
    if (destino === 'CONCLUIDA') tarefa.concluidaEm = new Date().toISOString();
    // Reemite a lista a partir das colunas já alteradas: `colunas` é derivado de `tarefas`, e se só reemitíssemos a
    // lista antiga o agrupamento desfaria o movimento na tela.
    const c = this.colunas();
    this.tarefas.set([...c.A_FAZER, ...c.EM_ANDAMENTO, ...c.AGUARDANDO, ...c.CONCLUIDA]);

    this.service.mover(tarefa.id, destino, evento.currentIndex).subscribe({
      next: (salva) => {
        tarefa.concluidaEm = salva.concluidaEm;
        tarefa.atrasada = salva.atrasada;
      },
      error: (err) => {
        this.notificacao.error(apiErrorMessage(err));
        this.carregar();
      },
    });
  }

  private carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    const responsavel = this.soMinhas() ? this.auth.user()?.id ?? null : this.responsavelId();
    this.service.quadro({ responsavelId: responsavel, busca: this.textoBusca() }).subscribe({
      next: (t) => {
        this.tarefas.set(t);
        this.carregando.set(false);
      },
      error: (err) => {
        this.erro.set(apiErrorMessage(err));
        this.carregando.set(false);
      },
    });
  }
}
