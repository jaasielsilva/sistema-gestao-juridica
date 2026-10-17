import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../../core/auth/api-error';
import { PASSWORD_MAX, PASSWORD_MIN } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthPage } from '../../../shared/auth-page/auth-page';
import { passwordsMatch } from '../../../shared/password-match';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatProgressSpinnerModule,
    AuthPage,
  ],
  templateUrl: './register.html',
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  protected readonly passwordMin = PASSWORD_MIN;
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly sentTo = signal<string | null>(null);

  protected readonly form = this.fb.nonNullable.group(
    {
      nome: ['', [Validators.required, Validators.maxLength(150)]],
      organizacao: ['', [Validators.required, Validators.maxLength(150)]],
      email: ['', [Validators.required, Validators.email]],
      senha: ['', [Validators.required, Validators.minLength(PASSWORD_MIN), Validators.maxLength(PASSWORD_MAX)]],
      confirmar: ['', [Validators.required]],
    },
    { validators: passwordsMatch('senha') },
  );

  protected submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    const { nome, organizacao, email, senha } = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.auth.register({ nome, organizacao, email, senha }).subscribe({
      next: () => {
        this.loading.set(false);
        this.sentTo.set(email);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err));
      },
    });
  }
}
