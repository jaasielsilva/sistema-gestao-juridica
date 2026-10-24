import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
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
  ],
  templateUrl: './processo-form.html',
  styleUrl: './processo-form.scss',
})
export class ProcessoForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProcessoService);
  private readonly clienteService = inject(ClienteService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly tipos = TIPOS_PROCESSO;
  protected readonly areas = AREAS_JURIDICAS;
  protected readonly tribunais = TRIBUNAIS;
  protected readonly situacoes = SITUACOES_PROCESSO;

  protected readonly id = signal<number | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly processo = signal<Processo | null>(null);
  protected readonly clientes = signal<Cliente[]>([]);

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
    this.carregarClientes();

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
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      },
    });
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

  protected formatDocCliente(cli: Cliente): string {
    return formatDocumento(cli.tipo, cli.documento);
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
    const req = id === null ? this.service.criar(body) : this.service.atualizar(id, body);

    req.subscribe({
      next: () => void this.router.navigate(['/processos']),
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err));
        if (err instanceof HttpErrorResponse && err.status === 409) {
          this.form.controls.numero.setErrors({ duplicado: true });
        }
      },
    });
  }

  private carregarClientes(): void {
    this.clienteService
      .listar({ busca: '', tipo: '', status: 'ativos', cidade: '', uf: '', page: 0, size: 200 })
      .subscribe({
        next: (page) => this.clientes.set(page.content),
        error: () => this.clientes.set([]),
      });
  }
}
