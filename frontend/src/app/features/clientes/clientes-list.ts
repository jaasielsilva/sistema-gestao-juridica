import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, startWith } from 'rxjs';
import { apiErrorMessage } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { formatDocumento, formatTelefone } from '../../shared/br-format';
import { Cliente, ClienteFiltro, ClienteResumo } from './cliente.models';
import { ClienteService } from './cliente.service';

@Component({
  selector: 'app-clientes-list',
  imports: [
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
  templateUrl: './clientes-list.html',
  styleUrl: './clientes-list.scss',
})
export class ClientesList {
  private readonly service = inject(ClienteService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly auth = inject(AuthService);

  protected readonly columns = ['nome', 'documento', 'tipo', 'contato', 'cidade', 'status', 'acoes'];

  protected readonly busca = new FormControl('', { nonNullable: true });
  protected readonly tipo = new FormControl<ClienteFiltro['tipo']>('', { nonNullable: true });
  protected readonly status = new FormControl<ClienteFiltro['status']>('ativos', { nonNullable: true });

  protected readonly clientes = signal<Cliente[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(10);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly resumo = signal<ClienteResumo | null>(null);

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
      .pipe(startWith(this.busca.value), debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.carregar(0));
    this.tipo.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.carregar(0));
    this.status.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.carregar(0));
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
    return ((partes[0]?.[0] ?? '') + (partes.length > 1 ? partes[partes.length - 1][0] : '')).toUpperCase();
  }

  protected mudarPagina(e: PageEvent): void {
    this.pageSize.set(e.pageSize);
    this.carregar(e.pageIndex);
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
    this.service.resumo().subscribe({ next: (r) => this.resumo.set(r), error: () => this.resumo.set(null) });
  }

  private carregar(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.service
      .listar({
        busca: this.busca.value,
        tipo: this.tipo.value,
        status: this.status.value,
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
