import { SelectionModel } from '@angular/cdk/collections';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import { apiErrorMessage } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { formatCnj, formatDocumento } from '../../shared/br-format';
import { Cliente } from '../clientes/cliente.models';
import { ClienteService } from '../clientes/cliente.service';
import { ProcessoDetalheDialog } from './processo-detalhe-dialog';
import {
  AREAS_JURIDICAS,
  Processo,
  ProcessoFiltro,
  ProcessoResumo,
  SITUACOES_PROCESSO,
  SituacaoProcesso,
  TIPOS_PROCESSO,
  TRIBUNAIS,
  TipoProcesso,
} from './processo.models';
import { ProcessoService } from './processo.service';

@Component({
  selector: 'app-processos-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatCheckboxModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatInputModule,
    MatFormFieldModule,
    MatMenuModule,
    MatSelectModule,
    MatProgressBarModule,
    MatTooltipModule,
  ],
  host: { class: 'fill-page' },
  templateUrl: './processos-list.html',
  styleUrl: './processos-list.scss',
})
export class ProcessosList implements OnInit {
  private readonly service = inject(ProcessoService);
  private readonly clienteService = inject(ClienteService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  protected readonly auth = inject(AuthService);

  protected readonly columns = [
    'select',
    'numero',
    'cliente',
    'tipo',
    'areaJuridica',
    'tribunal',
    'advogado',
    'situacao',
    'proximoPrazo',
    'acoes',
  ];

  protected readonly tipos = TIPOS_PROCESSO;
  protected readonly areas = AREAS_JURIDICAS;
  protected readonly tribunais = TRIBUNAIS;
  protected readonly situacoes = SITUACOES_PROCESSO;

  protected readonly busca = new FormControl('', { nonNullable: true });
  protected readonly clienteId = new FormControl<number | null>(null);
  protected readonly tipo = new FormControl<TipoProcesso | ''>('', { nonNullable: true });
  protected readonly areaJuridica = new FormControl('', { nonNullable: true });
  protected readonly tribunal = new FormControl('', { nonNullable: true });
  protected readonly situacao = new FormControl<SituacaoProcesso | ''>('', { nonNullable: true });

  protected readonly processos = signal<Processo[]>([]);
  protected readonly clientes = signal<Cliente[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(10);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resumo = signal<ProcessoResumo | null>(null);
  protected readonly filtrosAbertos = signal(false);
  protected readonly filtrosAtivos = signal(0);

  protected readonly selection = new SelectionModel<Processo>(true, []);

  protected readonly cartoes = computed(() => {
    const r = this.resumo();
    const tot = r?.total ?? 0;
    const pct = (val: number | undefined) => (tot > 0 && val !== undefined ? Math.round((val / tot) * 100) : 0);

    return [
      {
        tipo: 'total',
        icone: 'folder',
        rotulo: 'Total de Processos',
        valor: r?.total ?? 0,
        subtexto: '+12% em relação ao mês anterior',
        destaque: true,
      },
      {
        tipo: 'em-andamento',
        icone: 'schedule',
        rotulo: 'Em andamento',
        valor: r?.emAndamento ?? 0,
        subtexto: `${pct(r?.emAndamento)}% do total`,
        destaque: false,
      },
      {
        tipo: 'suspensos',
        icone: 'pause',
        rotulo: 'Suspensos',
        valor: r?.suspensos ?? 0,
        subtexto: `${pct(r?.suspensos)}% do total`,
        destaque: false,
      },
      {
        tipo: 'encerrados',
        icone: 'check_circle',
        rotulo: 'Encerrados',
        valor: r?.encerrados ?? 0,
        subtexto: `${pct(r?.encerrados)}% do total`,
        destaque: false,
      },
      {
        tipo: 'urgentes',
        icone: 'error',
        rotulo: 'Urgentes',
        valor: r?.urgentes ?? 0,
        subtexto: `${pct(r?.urgentes)}% do total`,
        destaque: false,
      },
    ];
  });

  ngOnInit(): void {
    this.carregarResumo();
    this.carregarClientes();

    this.busca.valueChanges
      .pipe(startWith(this.busca.value), debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.atualizarContagemFiltros();
        this.carregar(0);
      });

    this.clienteId.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.atualizarContagemFiltros();
      this.carregar(0);
    });

    this.tipo.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.atualizarContagemFiltros();
      this.carregar(0);
    });

    this.areaJuridica.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.atualizarContagemFiltros();
      this.carregar(0);
    });

    this.tribunal.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.atualizarContagemFiltros();
      this.carregar(0);
    });

    this.situacao.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.atualizarContagemFiltros();
      this.carregar(0);
    });
  }

  protected isAllSelected(): boolean {
    const numSelected = this.selection.selected.length;
    const numRows = this.processos().length;
    return numRows > 0 && numSelected === numRows;
  }

  protected isPartiallySelected(): boolean {
    const numSelected = this.selection.selected.length;
    const numRows = this.processos().length;
    return numSelected > 0 && numSelected < numRows;
  }

  protected toggleAllRows(): void {
    if (this.isAllSelected()) {
      this.selection.clear();
      return;
    }
    this.selection.select(...this.processos());
  }

  protected formatNumero(numero: string): string {
    return formatCnj(numero);
  }

  protected formatDoc(tipo: 'PF' | 'PJ', doc: string): string {
    return formatDocumento(tipo, doc);
  }

  protected formatData(iso: string | null): string {
    if (!iso) return '—';
    const parts = iso.split('-');
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return iso;
  }

  protected tipoLabel(tipo: TipoProcesso): string {
    return this.tipos.find((t) => t.value === tipo)?.label ?? tipo;
  }

  protected situacaoInfo(situacao: SituacaoProcesso): { label: string; cssClass: string } {
    return this.situacoes.find((s) => s.value === situacao) ?? { label: situacao, cssClass: 'badge--default' };
  }

  protected iniciais(nome: string): string {
    const partes = (nome ?? '').trim().split(/\s+/).filter((p) => /^\p{L}/u.test(p));
    if (!partes.length) return '?';
    return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  }

  protected mudarPagina(e: PageEvent): void {
    this.pageSize.set(e.pageSize);
    this.carregar(e.pageIndex);
  }

  protected alternarFiltros(): void {
    this.filtrosAbertos.update((v) => !v);
  }

  protected limparFiltros(): void {
    this.busca.setValue('', { emitEvent: false });
    this.clienteId.setValue(null, { emitEvent: false });
    this.tipo.setValue('', { emitEvent: false });
    this.areaJuridica.setValue('', { emitEvent: false });
    this.tribunal.setValue('', { emitEvent: false });
    this.situacao.setValue('', { emitEvent: false });
    this.atualizarContagemFiltros();
    this.carregar(0);
  }

  protected visualizar(p: Processo): void {
    this.dialog.open(ProcessoDetalheDialog, {
      data: p,
      width: '640px',
      maxWidth: 'calc(100vw - 32px)',
    });
  }

  protected copiarNumero(p: Processo): void {
    void navigator.clipboard.writeText(formatCnj(p.numero));
  }

  protected exportarCsv(): void {
    const lista = this.processos();
    if (!lista.length) return;

    const headers = [
      'Número CNJ',
      'Cliente',
      'Documento',
      'Tipo',
      'Área Jurídica',
      'Tribunal',
      'Comarca',
      'Vara',
      'Advogado Responsável',
      'OAB',
      'Situação',
      'Próximo Prazo',
      'Descrição do Prazo',
    ];

    const rows = lista.map((p) => [
      `"${formatCnj(p.numero)}"`,
      `"${p.clienteNome}"`,
      `"${formatDocumento(p.clienteTipo, p.clienteDocumento)}"`,
      `"${this.tipoLabel(p.tipo)}"`,
      `"${p.areaJuridica}"`,
      `"${p.tribunal}"`,
      `"${p.comarca ?? ''}"`,
      `"${p.vara ?? ''}"`,
      `"${p.advogadoNome}"`,
      `"${p.advogadoOab ?? ''}"`,
      `"${this.situacaoInfo(p.situacao).label}"`,
      `"${this.formatData(p.proximoPrazo)}"`,
      `"${p.proximoPrazoDescricao ?? ''}"`,
    ]);

    const csvContent = '\uFEFF' + [headers.join(';'), ...rows.map((r) => r.join(';'))].join('\r\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `processos_${new Date().toISOString().slice(0, 10)}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  private carregarResumo(): void {
    this.service.resumo().subscribe({
      next: (r) => this.resumo.set(r),
      error: () => this.resumo.set(null),
    });
  }

  private carregarClientes(): void {
    this.clienteService
      .listar({ busca: '', tipo: '', status: 'ativos', cidade: '', uf: '', page: 0, size: 100 })
      .subscribe({
        next: (page) => this.clientes.set(page.content),
        error: () => this.clientes.set([]),
      });
  }

  private atualizarContagemFiltros(): void {
    this.filtrosAtivos.set(
      Number(!!this.busca.value.trim()) +
        Number(this.clienteId.value !== null) +
        Number(!!this.tipo.value) +
        Number(!!this.areaJuridica.value.trim()) +
        Number(!!this.tribunal.value.trim()) +
        Number(!!this.situacao.value),
    );
  }

  private carregar(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.selection.clear();

    const filtro: ProcessoFiltro = {
      busca: this.busca.value,
      clienteId: this.clienteId.value,
      tipo: this.tipo.value,
      areaJuridica: this.areaJuridica.value,
      tribunal: this.tribunal.value,
      situacao: this.situacao.value,
      page,
      size: this.pageSize(),
    };

    this.service.listar(filtro).subscribe({
      next: (p) => {
        this.processos.set(p.content);
        this.total.set(p.totalElements);
        this.pageIndex.set(p.page);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      },
    });
  }
}
