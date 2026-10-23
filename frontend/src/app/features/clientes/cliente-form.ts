import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../core/auth/api-error';
import {
  cepValidator,
  documentoValidator,
  formatCep,
  formatDocumento,
  formatTelefone,
  telefoneValidator,
} from '../../shared/br-format';
import { Cliente, ClienteRequest, TipoPessoa, UFS } from './cliente.models';
import { ClienteService } from './cliente.service';

/** Cadastro e edicao de cliente (a mesma tela: com `:id` na rota carrega o cliente para editar). */
@Component({
  selector: 'app-cliente-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
  ],
  templateUrl: './cliente-form.html',
  styleUrl: './cliente-form.scss',
})
export class ClienteForm implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ClienteService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly ufs = UFS;
  protected readonly id = signal<number | null>(null);
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly cliente = signal<Cliente | null>(null);
  /** Tipo selecionado; alimenta a validacao do documento sem referenciar o proprio formulario. */
  private readonly tipoAtual = signal<TipoPessoa>('PF');

  protected readonly form = this.fb.nonNullable.group({
    tipo: ['PF' as TipoPessoa, [Validators.required]],
    nome: ['', [Validators.required, Validators.maxLength(200)]],
    documento: ['', [Validators.required, documentoValidator(() => this.tipoAtual())]],
    email: ['', [Validators.email, Validators.maxLength(200)]],
    telefone: ['', [telefoneValidator]],
    cep: ['', [cepValidator]],
    logradouro: ['', [Validators.maxLength(200)]],
    numero: ['', [Validators.maxLength(20)]],
    complemento: ['', [Validators.maxLength(100)]],
    bairro: ['', [Validators.maxLength(100)]],
    cidade: ['', [Validators.maxLength(100)]],
    uf: [''],
    observacoes: ['', [Validators.maxLength(2000)]],
  });

  protected get isPf(): boolean {
    return this.tipoAtual() === 'PF';
  }

  ngOnInit(): void {
    const raw = this.route.snapshot.paramMap.get('id');
    if (!raw) return;
    const id = Number(raw);
    this.id.set(id);
    this.loading.set(true);
    this.service.buscar(id).subscribe({
      next: (c) => {
        this.cliente.set(c);
        this.tipoAtual.set(c.tipo);
        this.form.setValue({
          tipo: c.tipo,
          nome: c.nome,
          documento: formatDocumento(c.tipo, c.documento),
          email: c.email ?? '',
          telefone: c.telefone ? formatTelefone(c.telefone) : '',
          cep: c.cep ? formatCep(c.cep) : '',
          logradouro: c.logradouro ?? '',
          numero: c.numero ?? '',
          complemento: c.complemento ?? '',
          bairro: c.bairro ?? '',
          cidade: c.cidade ?? '',
          uf: c.uf ?? '',
          observacoes: c.observacoes ?? '',
        });
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.error.set(apiErrorMessage(err));
        this.loading.set(false);
      },
    });
  }

  protected mudarTipo(): void {
    this.tipoAtual.set(this.form.controls.tipo.value);
    const doc = this.form.controls.documento;
    doc.setValue(formatDocumento(this.tipoAtual(), doc.value));
    doc.updateValueAndValidity();
  }

  protected mascararDocumento(): void {
    const c = this.form.controls.documento;
    c.setValue(formatDocumento(this.tipoAtual(), c.value));
  }

  protected mascararTelefone(): void {
    const c = this.form.controls.telefone;
    c.setValue(formatTelefone(c.value));
  }

  protected mascararCep(): void {
    const c = this.form.controls.cep;
    c.setValue(formatCep(c.value));
  }

  protected salvar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const vazioNulo = (s: string) => (s.trim() ? s.trim() : null);
    const body: ClienteRequest = {
      tipo: v.tipo,
      nome: v.nome.trim(),
      documento: v.documento,
      email: vazioNulo(v.email),
      telefone: vazioNulo(v.telefone),
      cep: vazioNulo(v.cep),
      logradouro: vazioNulo(v.logradouro),
      numero: vazioNulo(v.numero),
      complemento: vazioNulo(v.complemento),
      bairro: vazioNulo(v.bairro),
      cidade: vazioNulo(v.cidade),
      uf: vazioNulo(v.uf),
      observacoes: vazioNulo(v.observacoes),
    };
    this.saving.set(true);
    this.error.set(null);
    const id = this.id();
    (id === null ? this.service.criar(body) : this.service.atualizar(id, body)).subscribe({
      next: () => void this.router.navigate(['/clientes']),
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err));
        if (err instanceof HttpErrorResponse && err.status === 409) {
          this.form.controls.documento.setErrors({ duplicado: true });
        }
      },
    });
  }
}
