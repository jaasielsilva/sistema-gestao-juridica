import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import Swal from 'sweetalert2';
import { apiErrorMessage } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notification/notification.service';
import { Responsavel, ResponsavelService } from '../../core/usuarios/responsavel.service';
import { formatCnj } from '../../shared/br-format';
import { PrazoDialog } from './prazo-dialog';
import {
  PERIODOS_PRAZO,
  Prazo,
  PrazoFiltro,
  PrazoResumo,
  SITUACOES_PRAZO,
  SituacaoPrazo,
  TipoPrazo,
  dataBr,
} from './prazo.models';
import { PrazoService } from './prazo.service';

/** Cor do selo (badge) de cada situação, no padrão visual da lista de processos. */
const COR_SITUACAO: Record<SituacaoPrazo, string> = {
  HOJE: 'ambar',
  PROXIMO: 'azul',
  VENCIDO: 'vermelho',
  CUMPRIDO: 'ok',
};

@Component({
  selector: 'app-prazos-list',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatSelectModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  host: { class: 'fill-page' },
  templateUrl: './prazos-list.html',
  styleUrl: './prazos-list.scss',
})
export class PrazosList implements OnInit {
  private readonly service = inject(PrazoService);
  private readonly responsaveisService = inject(ResponsavelService);
  private readonly dialog = inject(MatDialog);
  private readonly notificacao = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  /** Caixa de busca: cliente, número do processo, tipo de prazo ou descrição. */
  protected readonly busca = new FormControl('', { nonNullable: true });

  protected readonly colunas = ['vencimento', 'situacao', 'cliente', 'processo', 'tipo', 'responsavel', 'acoes'];
  protected readonly situacoes = SITUACOES_PRAZO;
  protected readonly periodos = PERIODOS_PRAZO;
  protected readonly cnj = formatCnj;
  protected readonly data = dataBr;

  protected readonly prazos = signal<Prazo[]>([]);
  protected readonly total = signal(0);
  protected readonly resumo = signal<PrazoResumo | null>(null);
  protected readonly tipos = signal<TipoPrazo[]>([]);
  protected readonly responsaveis = signal<Responsavel[]>([]);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly filtro = signal<PrazoFiltro>(filtroPadrao());

  /** Categorias distintas do catálogo, para o filtro. */
  protected readonly categorias = computed(() => {
    const vistas = new Map<string, string>();
    for (const t of this.tipos()) vistas.set(t.categoria, t.categoriaRotulo);
    return [...vistas].map(([codigo, rotulo]) => ({ codigo, rotulo }));
  });

  /** Quantos filtros estão ligados (o botão "Limpar" só aparece com algum ligado). */
  protected readonly filtrosAtivos = computed(() => {
    const f = this.filtro();
    return [(f.busca ?? '') !== '', f.situacao !== '', f.dias !== '', f.categoria !== '', f.responsavelId !== ''].filter(Boolean)
      .length;
  });

  protected readonly cartoes = computed(() => {
    const r = this.resumo();
    return [
      { rotulo: 'Vencem hoje', valor: r?.hoje ?? 0, situacao: 'HOJE' as SituacaoPrazo, icone: 'today', cor: 'ambar' },
      { rotulo: 'Próximos', valor: r?.proximos ?? 0, situacao: 'PROXIMO' as SituacaoPrazo, icone: 'schedule', cor: 'azul' },
      { rotulo: 'Vencidos', valor: r?.vencidos ?? 0, situacao: 'VENCIDO' as SituacaoPrazo, icone: 'error_outline', cor: 'vermelho' },
      { rotulo: 'Cumpridos (30 dias)', valor: r?.cumpridosUltimos30Dias ?? 0, situacao: 'CUMPRIDO' as SituacaoPrazo, icone: 'check_circle', cor: 'verde' },
    ];
  });

  ngOnInit(): void {
    this.busca.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((texto) => this.atualizarFiltro({ busca: texto }));
    this.service.tipos().subscribe({ next: (t) => this.tipos.set(t), error: () => undefined });
    this.responsaveisService.listar().subscribe({ next: (r) => this.responsaveis.set(r), error: () => undefined });
    this.recarregar();
  }

  protected rotuloSituacao(s: SituacaoPrazo): string {
    return SITUACOES_PRAZO.find((x) => x.value === s)?.label ?? s;
  }

  protected corSituacao(s: SituacaoPrazo): string {
    return COR_SITUACAO[s] ?? 'cinza';
  }

  protected atualizarFiltro(parcial: Partial<PrazoFiltro>): void {
    this.filtro.update((f) => ({ ...f, ...parcial, page: 0 }));
    this.carregarLista();
  }

  protected filtrarSituacao(situacao: SituacaoPrazo): void {
    this.atualizarFiltro({ situacao: this.filtro().situacao === situacao ? '' : situacao });
  }

  protected limpar(): void {
    this.busca.setValue('', { emitEvent: false });
    this.filtro.set(filtroPadrao());
    this.carregarLista();
  }

  protected paginar(e: PageEvent): void {
    this.filtro.update((f) => ({ ...f, page: e.pageIndex, size: e.pageSize }));
    this.carregarLista();
  }

  protected novo(): void {
    this.dialog.open(PrazoDialog, { data: {} }).afterClosed().subscribe((salvo) => {
      if (salvo) {
        this.notificacao.success('Prazo cadastrado.');
        this.recarregar();
      }
    });
  }

  protected editar(prazo: Prazo): void {
    this.dialog.open(PrazoDialog, { data: { prazo } }).afterClosed().subscribe((salvo) => {
      if (salvo) {
        this.notificacao.success('Prazo atualizado.');
        this.recarregar();
      }
    });
  }

  protected cumprir(prazo: Prazo): void {
    this.service.cumprir(prazo.id).subscribe({
      next: () => {
        this.notificacao.success('Prazo marcado como cumprido.');
        this.recarregar();
      },
      error: (err) => this.notificacao.error(apiErrorMessage(err)),
    });
  }

  protected reabrir(prazo: Prazo): void {
    this.service.reabrir(prazo.id).subscribe({
      next: () => {
        this.notificacao.success('Prazo reaberto.');
        this.recarregar();
      },
      error: (err) => this.notificacao.error(apiErrorMessage(err)),
    });
  }

  protected async excluir(prazo: Prazo): Promise<void> {
    const r = await Swal.fire({
      title: 'Excluir prazo?',
      text: `${prazo.tipoRotulo} — vence em ${dataBr(prazo.dataVencimento)}. Esta ação não pode ser desfeita.`,
      icon: 'warning',
      showCancelButton: true,
      confirmButtonText: 'Excluir',
      cancelButtonText: 'Cancelar',
    });
    if (!r.isConfirmed) return;
    this.service.excluir(prazo.id).subscribe({
      next: () => {
        this.notificacao.success('Prazo excluído.');
        this.recarregar();
      },
      error: (err) => this.notificacao.error(apiErrorMessage(err)),
    });
  }

  private recarregar(): void {
    this.carregarLista();
    this.service.resumo().subscribe({ next: (r) => this.resumo.set(r), error: () => undefined });
  }

  private carregarLista(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.service.listar(this.filtro()).subscribe({
      next: (pagina) => {
        this.prazos.set(pagina.content);
        this.total.set(pagina.totalElements);
        this.carregando.set(false);
      },
      error: (err) => {
        this.erro.set(apiErrorMessage(err));
        this.carregando.set(false);
      },
    });
  }
}

function filtroPadrao(): PrazoFiltro {
  return { situacao: '', dias: '', categoria: '', responsavelId: '', busca: '', page: 0, size: 10 };
}
