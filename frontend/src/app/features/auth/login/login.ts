import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { apiErrorMessage } from '../../../core/auth/api-error';
import { LoginResult } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { BrandedAuthPage } from '../../../shared/branded-auth-page/branded-auth-page';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    BrandedAuthPage,
  ],
  templateUrl: './login.html',
})
export class Login implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly hidePassword = signal(true);
  protected readonly registrationEnabled = signal(false);
  /** Preenchido quando a senha esta certa mas o MFA esta ativo: passa a pedir o codigo. */
  protected readonly mfaToken = signal<string | null>(null);
  /** O backend recusou por e-mail nao verificado: oferece reenviar o link. */
  protected readonly needsVerification = signal(false);

  protected readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    senha: ['', [Validators.required]],
  });

  protected readonly mfaForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.minLength(6)]],
  });

  ngOnInit(): void {
    this.auth.registrationEnabled().subscribe((enabled) => this.registrationEnabled.set(enabled));
  }

  protected submit(): void {
    if (this.form.invalid || this.loading()) {
      this.form.markAllAsTouched();
      return;
    }
    this.run(this.auth.login(this.form.getRawValue()));
  }

  protected submitMfa(): void {
    const token = this.mfaToken();
    if (!token || this.mfaForm.invalid || this.loading()) {
      this.mfaForm.markAllAsTouched();
      return;
    }
    this.run(this.auth.verifyMfa(token, this.mfaForm.getRawValue().codigo.trim()));
  }

  protected backToCredentials(): void {
    this.mfaToken.set(null);
    this.mfaForm.reset();
    this.error.set(null);
  }

  protected resendVerification(): void {
    const email = this.form.getRawValue().email;
    this.auth.resendVerification(email).subscribe({
      next: (r) => {
        this.needsVerification.set(false);
        this.error.set(null);
        this.info.set(r.message);
      },
      error: (err: unknown) => this.error.set(apiErrorMessage(err)),
    });
  }

  private run(request: Observable<LoginResult>): void {
    this.loading.set(true);
    this.error.set(null);
    this.info.set(null);
    this.needsVerification.set(false);

    request.subscribe({
      next: (result) => {
        this.loading.set(false);
        if (result.status === 'mfa') {
          this.mfaToken.set(result.mfaToken);
          return;
        }
        void this.router.navigateByUrl(this.safeReturnUrl());
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err));
        this.needsVerification.set(err instanceof HttpErrorResponse && err.status === 403);
      },
    });
  }

  /** Aceita so caminhos internos, evitando open redirect via ?returnUrl=https://site-malicioso. */
  private safeReturnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('returnUrl');
    return url && url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/login') ? url : '/';
  }
}
