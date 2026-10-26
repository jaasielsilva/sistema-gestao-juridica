import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { RouterLink } from '@angular/router';
import { Subscription, debounceTime, distinctUntilChanged, map, startWith } from 'rxjs';
import { apiErrorMessage } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { formatDocumento, formatTelefone } from '../../shared/br-format';
import { ClienteDetalheDialog } from './cliente-detalhe-dialog';
import { Cliente, ClienteFiltro, ClienteResumo, UFS } from './cliente.models';
import { ClienteService } from './cliente.service';

/** Abaixo disso o backend recusa a busca (ela varreria muitos registros); o front nem envia. */
const MIN_BUSCA = 3;

@Component({
  selector: 'app-clientes-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
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
  templateUrl: './clientes-list.html',
  styleUrl: './clientes-list.scss',
})
export class ClientesList {
  private readonly service = inject(ClienteService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialog = inject(MatDialog);
  protected readonly auth = inject(AuthService);

  protected readonly columns = [
    'nome',
    'documento',
    'tipo',
    'contato',
    'cidade',
    'status',
    'acoes',
  ];
  protected readonly ufs = UFS;

  protected readonly busca = new FormControl('', { nonNullable: true });
  protected readonly tipo = new FormControl<ClienteFiltro['tipo']>('', { nonNullable: true });
  protected readonly status = new FormControl<ClienteFiltro['status']>('ativos', {
    nonNullable: true,
  });
  protected readonly cidade = new FormControl('', { nonNullable: true });
  protected readonly uf = new FormControl('', { nonNullable: true });

  protected readonly clientes = signal<Cliente[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(10);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private requisicao?: Subscription;
  protected readonly resumo = signal<ClienteResumo | null>(null);
  protected readonly filtrosAvancadosAbertos = signal(false);
  protected readonly filtrosAtivos = signal(1);

  protected readonly cartoes = computed(() => {
    const r = this.resumo();
    return [
      { icone: 'groups', rotulo: 'Total de clientes', valor: r?.total },
      { icone: 'check_circle', rotulo: 'Ativos', valor: r?.ativos },
      { icone: 'person_off', rotulo: 'Inativos', valor: r?.inativos },
      { icone: 'person', rotulo: 'Pessoas físicas', valor: r?.pessoasFisicas },
      { icone: 'business', rotulo: 'Pessoas jurídicas', valor: r?.pessoasJuridicas },
    ];
  });

  constructor() {
    this.carregarResumo();
    this.busca.valueChanges
      .pipe(
        startWith(this.busca.value),
        debounceTime(300),
        map((v) => this.termoBusca(v)),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.atualizarContagemFiltros();
        this.carregar(0);
      });
    this.tipo.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.atualizarContagemFiltros();
      this.carregar(0);
    });
    this.status.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.atualizarContagemFiltros();
      this.carregar(0);
    });
    this.cidade.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.atualizarContagemFiltros();
        this.carregar(0);
      });
    this.uf.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.atualizarContagemFiltros();
      this.carregar(0);
    });
  }

  protected documento(c: Cliente): string {
    return formatDocumento(c.tipo, c.documento);
  }

  protected telefone(c: Cliente): string {
    return c.telefone ? formatTelefone(c.telefone) : '';
  }

  protected iniciais(nome: string): string {
    const partes = nome.split(/\s+/).filter((p) => /^\p{L}/u.test(p));
    if (!partes.length) return '?';
    return (
      (partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')
    ).toUpperCase();
  }

  protected mudarPagina(e: PageEvent): void {
    this.pageSize.set(e.pageSize);
    this.carregar(e.pageIndex);
  }

  protected limparFiltros(): void {
    this.busca.setValue('', { emitEvent: false });
    this.tipo.setValue('', { emitEvent: false });
    this.status.setValue('todos', { emitEvent: false });
    this.cidade.setValue('', { emitEvent: false });
    this.uf.setValue('', { emitEvent: false });
    this.atualizarContagemFiltros();
    this.carregar(0);
  }

  protected alternarFiltrosAvancados(): void {
    this.filtrosAvancadosAbertos.update((aberto) => !aberto);
  }

  protected visualizar(c: Cliente): void {
    this.dialog.open(ClienteDetalheDialog, {
      data: c,
      width: '560px',
      maxWidth: 'calc(100vw - 32px)',
    });
  }

  protected alternarStatus(c: Cliente): void {
    const acao = c.ativo ? this.service.inativar(c.id) : this.service.reativar(c.id);
    acao.subscribe({
      next: () => {
        this.carregar(this.pageIndex());
        this.carregarResumo();
      },
      error: (err: unknown) => this.error.set(apiErrorMessage(err)),
    });
  }

  private carregarResumo(): void {
    this.service
      .resumo()
      .subscribe({ next: (r) => this.resumo.set(r), error: () => this.resumo.set(null) });
  }

  private atualizarContagemFiltros(): void {
    this.filtrosAtivos.set(
      Number(!!this.termoBusca(this.busca.value)) +
        Number(!!this.tipo.value) +
        Number(this.status.value !== 'todos') +
        Number(!!this.cidade.value.trim()) +
        Number(!!this.uf.value),
    );
  }

  private termoBusca(valor: string): string {
    const termo = valor.trim();
    return termo.length >= MIN_BUSCA ? termo : '';
  }

  private carregar(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    // Cancela a consulta anterior: respostas antigas nao sobrescrevem a mais recente.
    this.requisicao?.unsubscribe();
    this.requisicao = this.service
      .listar({
        busca: this.termoBusca(this.busca.value),
        tipo: this.tipo.value,
        status: this.status.value,
        cidade: this.cidade.value,
        uf: this.uf.value,
        page,
        size: this.pageSize(),
      })
      .subscribe({
        next: (p) => {
          this.clientes.set(p.content);
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
