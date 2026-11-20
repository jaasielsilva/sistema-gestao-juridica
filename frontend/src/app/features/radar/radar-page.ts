import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';
import { apiErrorMessage } from '../../core/auth/api-error';
import { NotificationService } from '../../core/notification/notification.service';
import { formatCnj } from '../../shared/br-format';
import { ETAPAS_PROCESSO, EtapaProcesso, TIPOS_PROCESSO } from '../processos/processo.models';
import { ProcessoService } from '../processos/processo.service';
import { FAIXAS_RADAR, RadarFaixa, RadarItem, RadarResumo } from './radar.models';
import { RadarService } from './radar.service';

/** Cor do selo de cada faixa, no padrão visual da lista de processos. */
const COR_FAIXA: Record<RadarFaixa, string> = {
  TRAVADO: 'vermelho',
  ATENCAO: 'ambar',
  OK: 'ok',
};

/** Radar de processos: onde cada processo ativo está no fluxo e há quantos dias está parado na etapa. */
@Component({
  selector: 'app-radar',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatButtonModule,
    MatIconModule,
    MatSelectModule,
    MatProgressBarModule,
  ],
  host: { class: 'fill-page' },
  templateUrl: './radar-page.html',
  styleUrl: './radar-page.scss',
})
export class RadarPage implements OnInit {
  private readonly radar = inject(RadarService);
  private readonly processos = inject(ProcessoService);
  private readonly notificacao = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);

  /** Caixa de busca: número do processo, cliente (nome ou documento) ou advogado. */
  protected readonly busca = new FormControl('', { nonNullable: true });
  private readonly textoBusca = signal('');

  protected readonly colunas = ['numero', 'cliente', 'tipo', 'etapa', 'dias', 'situacao', 'responsavel'];
  protected readonly etapas = ETAPAS_PROCESSO;
  protected readonly cnj = formatCnj;

  protected readonly itens = signal<RadarItem[]>([]);
  protected readonly total = signal(0);
  protected readonly resumo = signal<RadarResumo | null>(null);
  protected readonly faixa = signal<RadarFaixa | ''>('');
  protected readonly etapa = signal<EtapaProcesso | ''>('');
  protected readonly pagina = signal(0);
  protected readonly tamanho = signal(10);
  protected readonly carregando = signal(false);
  protected readonly erro = signal<string | null>(null);

  protected readonly filtrosAtivos = computed(
    () => (this.faixa() ? 1 : 0) + (this.etapa() ? 1 : 0) + (this.textoBusca() ? 1 : 0),
  );

  protected readonly cartoes = computed(() => {
    const r = this.resumo();
    return [
      { rotulo: 'Em andamento', valor: r?.total ?? 0, faixa: '' as RadarFaixa | '', icone: 'folder_open', cor: 'azul', sub: 'processos ativos' },
      { rotulo: 'Travados', valor: r?.travados ?? 0, faixa: 'TRAVADO' as RadarFaixa, icone: 'error_outline', cor: 'vermelho', sub: 'mais de 7 dias na etapa' },
      { rotulo: 'Atenção', valor: r?.atencao ?? 0, faixa: 'ATENCAO' as RadarFaixa, icone: 'warning_amber', cor: 'ambar', sub: '4 a 7 dias na etapa' },
      { rotulo: 'Fluindo', valor: r?.fluindo ?? 0, faixa: 'OK' as RadarFaixa, icone: 'trending_up', cor: 'verde', sub: 'até 3 dias na etapa' },
    ];
  });

  ngOnInit(): void {
    this.busca.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((texto) => {
        this.textoBusca.set(texto.trim());
        this.pagina.set(0);
        this.carregarLista();
      });
    this.recarregar();
  }

  protected rotuloTipo(tipo: string): string {
    return TIPOS_PROCESSO.find((t) => t.value === tipo)?.label ?? tipo;
  }

  protected rotuloFaixa(f: RadarFaixa): string {
    return FAIXAS_RADAR.find((x) => x.value === f)?.label ?? f;
  }

  protected corFaixa(f: RadarFaixa): string {
    return COR_FAIXA[f] ?? 'cinza';
  }

  protected filtrarFaixa(faixa: RadarFaixa | ''): void {
    this.faixa.set(faixa === '' || this.faixa() === faixa ? '' : faixa);
    this.pagina.set(0);
    this.carregarLista();
  }

  protected filtrarEtapa(etapa: EtapaProcesso): void {
    this.etapa.set(this.etapa() === etapa ? '' : etapa);
    this.pagina.set(0);
    this.carregarLista();
  }

  protected limpar(): void {
    this.busca.setValue('', { emitEvent: false });
    this.textoBusca.set('');
    this.faixa.set('');
    this.etapa.set('');
    this.pagina.set(0);
    this.carregarLista();
  }

  protected paginar(e: PageEvent): void {
    this.pagina.set(e.pageIndex);
    this.tamanho.set(e.pageSize);
    this.carregarLista();
  }

  protected mudarEtapa(item: RadarItem, nova: EtapaProcesso): void {
    if (nova === item.etapa) return;
    this.processos.mudarEtapa(item.id, nova).subscribe({
      next: () => {
        this.notificacao.success('Etapa atualizada.');
        this.recarregar();
      },
      error: (err) => {
        this.notificacao.error(apiErrorMessage(err));
        this.carregarLista(); // volta o seletor ao valor real
      },
    });
  }

  private recarregar(): void {
    this.carregarLista();
    this.radar.resumo().subscribe({ next: (r) => this.resumo.set(r), error: () => undefined });
  }

  private carregarLista(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.radar
      .listar({ faixa: this.faixa(), etapa: this.etapa(), busca: this.textoBusca(), page: this.pagina(), size: this.tamanho() })
      .subscribe({
        next: (p) => {
          this.itens.set(p.content);
          this.total.set(p.totalElements);
          this.carregando.set(false);
        },
        error: (err) => {
          this.erro.set(apiErrorMessage(err));
          this.carregando.set(false);
        },
      });
  }
}
