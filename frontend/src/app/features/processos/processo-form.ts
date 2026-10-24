import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { apiErrorMessage } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { NotificationService } from '../../core/notification/notification.service';
import { cnjValidator, formatCnj, formatDocumento } from '../../shared/br-format';
import { Cliente } from '../clientes/cliente.models';
import { ClienteService } from '../clientes/cliente.service';
import {
  AREAS_JURIDICAS,
  Processo,
  ProcessoRequest,
  SITUACOES_PROCESSO,
  SituacaoProcesso,
  TIPOS_PROCESSO,
  TRIBUNAIS,
  TipoProcesso,
} from './processo.models';
import { ProcessoService } from './processo.service';

/** Cadastro e edicao de processo (a mesma tela: com `:id` na rota carrega o processo para editar). */
@Component({
  selector: 'app-processo-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatAutocompleteModule,
  ],
  templateUrl: './processo-form.html',
  styleUrl: './processo-form.scss',
})
export class ProcessoForm implements OnInit, OnDestroy {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProcessoService);
  private readonly clienteService = inject(ClienteService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notify = inject(NotificationService);
  private readonly destroy$ = new Subject<void>();

  protected readonly tipos = TIPOS_PROCESSO;
  protected readonly areas = AREAS_JURIDICAS;
  protected readonly tribunais = TRIBUNAIS;
  protected readonly situacoes = SITUACOES_PROCESSO;

  protected readonly id = signal<number | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly processo = signal<Processo | null>(null);

  // --- Autocomplete de cliente ---
  /** Sugestoes retornadas pela API conforme o usuario digita. */
  protected readonly clientesSugeridos = signal<Cliente[]>([]);
  /** Indica que a busca de sugestoes esta em andamento. */
  protected readonly buscandoClientes = signal(false);
  /** Campo de texto visivel para o autocomplete (nao vai para o formulario). */
  protected readonly clienteBusca = new FormControl('');
  /** Cliente efetivamente selecionado (id que vai para o form). */
  protected readonly clienteSelecionado = signal<Cliente | null>(null);

  protected readonly form = this.fb.group({
    numero: ['', [Validators.required, cnjValidator]],
    clienteId: [null as number | null, [Validators.required]],
    tipo: ['CIVIL' as TipoProcesso, [Validators.required]],
    areaJuridica: ['Direito Civil', [Validators.required, Validators.maxLength(100)]],
    tribunal: ['TJSP - Comarca de São Paulo', [Validators.required, Validators.maxLength(150)]],
    comarca: ['', [Validators.maxLength(150)]],
    vara: ['', [Validators.maxLength(150)]],
    advogadoNome: ['', [Validators.required, Validators.maxLength(200)]],
    advogadoOab: ['', [Validators.maxLength(30)]],
    situacao: ['EM_ANDAMENTO' as SituacaoProcesso, [Validators.required]],
    proximoPrazo: [''],
    proximoPrazoDescricao: ['', [Validators.maxLength(200)]],
    observacoes: ['', [Validators.maxLength(2000)]],
  });

  ngOnInit(): void {
    this.iniciarAutocompletCliente();

    const loggedUser = this.auth.user();
    if (loggedUser) {
      this.form.controls.advogadoNome.setValue(loggedUser.nome);
    }

    const raw = this.route.snapshot.paramMap.get('id');
    if (!raw) return;

    const id = Number(raw);
    this.id.set(id);
    this.loading.set(true);

    this.service.buscar(id).subscribe({
      next: (p) => {
        this.processo.set(p);
        this.form.setValue({
          numero: formatCnj(p.numero),
          clienteId: p.clienteId,
          tipo: p.tipo,
          areaJuridica: p.areaJuridica,
          tribunal: p.tribunal,
          comarca: p.comarca ?? '',
          vara: p.vara ?? '',
          advogadoNome: p.advogadoNome,
          advogadoOab: p.advogadoOab ?? '',
          situacao: p.situacao,
          proximoPrazo: p.proximoPrazo ?? '',
          proximoPrazoDescricao: p.proximoPrazoDescricao ?? '',
          observacoes: p.observacoes ?? '',
        });
        // Ao editar, carrega o cliente selecionado para exibir no campo
        this.clienteService.buscar(p.clienteId).subscribe({
          next: (c) => {
            this.clienteSelecionado.set(c);
            this.clienteBusca.setValue(this.labelCliente(c), { emitEvent: false });
          },
          error: () => undefined,
        });
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /** Monta o texto de exibicao do cliente no campo de autocomplete. */
  protected labelCliente(cli: Cliente | null): string {
    if (!cli) return '';
    return `${cli.nome} — ${formatDocumento(cli.tipo, cli.documento)}`;
  }

  /** Chamado pelo mat-autocomplete quando o usuario seleciona uma opcao. */
  protected selecionarCliente(cli: Cliente): void {
    this.clienteSelecionado.set(cli);
    this.form.controls.clienteId.setValue(cli.id);
    this.form.controls.clienteId.markAsTouched();
  }

  /** Chamado quando o usuario limpa o campo manualmente. */
  protected limparCliente(): void {
    this.clienteSelecionado.set(null);
    this.form.controls.clienteId.setValue(null);
    this.clienteBusca.setValue('');
    this.clientesSugeridos.set([]);
  }

  protected mascararNumero(): void {
    const c = this.form.controls.numero;
    if (c.value) {
      c.setValue(formatCnj(c.value));
    }
  }

  protected formatNumero(v: string): string {
    return formatCnj(v);
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const v = this.form.getRawValue();
    const vazioNulo = (s: string | null | undefined) => (s && s.trim() ? s.trim() : null);

    const body: ProcessoRequest = {
      numero: v.numero!.trim(),
      clienteId: v.clienteId!,
      tipo: v.tipo!,
      areaJuridica: v.areaJuridica!.trim(),
      tribunal: v.tribunal!.trim(),
      comarca: vazioNulo(v.comarca),
      vara: vazioNulo(v.vara),
      advogadoNome: v.advogadoNome!.trim(),
      advogadoOab: vazioNulo(v.advogadoOab),
      situacao: v.situacao!,
      proximoPrazo: vazioNulo(v.proximoPrazo),
      proximoPrazoDescricao: vazioNulo(v.proximoPrazoDescricao),
      observacoes: vazioNulo(v.observacoes),
    };

    this.saving.set(true);
    this.error.set(null);

    const id = this.id();
    const isNovo = id === null;
    const req = isNovo ? this.service.criar(body) : this.service.atualizar(id, body);

    req.subscribe({
      next: () => {
        this.notify.success(isNovo ? 'Processo cadastrado com sucesso!' : 'Processo atualizado com sucesso!');
        void this.router.navigate(['/processos']);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        const msg = apiErrorMessage(err);
        this.error.set(msg);
        this.notify.error(msg);
        if (err instanceof HttpErrorResponse && err.status === 409) {
          this.form.controls.numero.setErrors({ duplicado: true });
        }
      },
    });
  }

  /** Escuta o campo de busca e dispara a consulta na API com debounce de 300ms. */
  private iniciarAutocompletCliente(): void {
    this.clienteBusca.valueChanges.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$),
    ).subscribe((termo) => {
      // Se o usuario apagou o campo, limpa a selecao
      if (!termo || termo.trim().length === 0) {
        this.clientesSugeridos.set([]);
        if (this.clienteSelecionado()) {
          this.clienteSelecionado.set(null);
          this.form.controls.clienteId.setValue(null);
        }
        return;
      }

      // Se o texto ainda e o label do cliente selecionado, nao rebusca
      const selecionado = this.clienteSelecionado();
      if (selecionado && termo === this.labelCliente(selecionado)) {
        return;
      }

      // Busca na API: apenas ativos, maximo 10 sugestoes
      this.buscandoClientes.set(true);
      this.clienteService
        .listar({ busca: termo.trim(), tipo: '', status: 'ativos', cidade: '', uf: '', page: 0, size: 10 })
        .subscribe({
          next: (page) => {
            this.clientesSugeridos.set(page.content);
            this.buscandoClientes.set(false);
          },
          error: () => this.buscandoClientes.set(false),
        });
    });
  }
}
