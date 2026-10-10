import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import * as QRCode from 'qrcode';
import { apiErrorMessage } from '../../../core/auth/api-error';
import { MfaSetup, PASSWORD_MAX, PASSWORD_MIN } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { passwordsMatch } from '../../../shared/password-match';

@Component({
  selector: 'app-security',
  imports: [
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './security.html',
  styleUrl: './security.scss',
})
export class Security {
  private readonly fb = inject(FormBuilder);
  protected readonly auth = inject(AuthService);

  protected readonly passwordMin = PASSWORD_MIN;

  // ---- MFA ----
  protected readonly mfaBusy = signal(false);
  protected readonly mfaError = signal<string | null>(null);
  protected readonly setup = signal<MfaSetup | null>(null);
  protected readonly qrDataUrl = signal<string | null>(null);
  protected readonly recoveryCodes = signal<string[] | null>(null);

  protected readonly enableForm = this.fb.nonNullable.group({
    codigo: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
  });
  protected readonly disableForm = this.fb.nonNullable.group({
    senha: ['', [Validators.required]],
    codigo: ['', [Validators.required]],
  });

  // ---- Senha ----
  protected readonly pwdBusy = signal(false);
  protected readonly pwdError = signal<string | null>(null);
  protected readonly pwdDone = signal<string | null>(null);
  protected readonly passwordForm = this.fb.nonNullable.group(
    {
      senhaAtual: ['', [Validators.required]],
      novaSenha: ['', [Validators.required, Validators.minLength(PASSWORD_MIN), Validators.maxLength(PASSWORD_MAX)]],
      confirmar: ['', [Validators.required]],
    },
    { validators: passwordsMatch('novaSenha') },
  );

  // ---- Dispositivos ----
  protected readonly devicesError = signal<string | null>(null);

  protected startMfa(): void {
    this.mfaBusy.set(true);
    this.mfaError.set(null);
    this.auth.mfaSetup().subscribe({
      next: async (s) => {
        this.setup.set(s);
        this.qrDataUrl.set(await QRCode.toDataURL(s.otpauthUri, { width: 200, margin: 1 }));
        this.mfaBusy.set(false);
      },
      error: (err: unknown) => {
        this.mfaBusy.set(false);
        this.mfaError.set(apiErrorMessage(err));
      },
    });
  }

  protected cancelMfaSetup(): void {
    this.setup.set(null);
    this.qrDataUrl.set(null);
    this.enableForm.reset();
    this.mfaError.set(null);
  }

  protected enableMfa(): void {
    if (this.enableForm.invalid || this.mfaBusy()) {
      this.enableForm.markAllAsTouched();
      return;
    }
    this.mfaBusy.set(true);
    this.mfaError.set(null);
    this.auth.mfaEnable(this.enableForm.getRawValue().codigo).subscribe({
      next: (codes) => {
        this.mfaBusy.set(false);
        this.recoveryCodes.set(codes);
        this.cancelMfaSetup();
      },
      error: (err: unknown) => {
        this.mfaBusy.set(false);
        this.mfaError.set(apiErrorMessage(err));
      },
    });
  }

  protected dismissRecoveryCodes(): void {
    this.recoveryCodes.set(null);
  }

  protected disableMfa(): void {
    if (this.disableForm.invalid || this.mfaBusy()) {
      this.disableForm.markAllAsTouched();
      return;
    }
    const { senha, codigo } = this.disableForm.getRawValue();
    this.mfaBusy.set(true);
    this.mfaError.set(null);
    this.auth.mfaDisable(senha, codigo.trim()).subscribe({
      next: () => {
        this.mfaBusy.set(false);
        this.disableForm.reset();
      },
      error: (err: unknown) => {
        this.mfaBusy.set(false);
        this.mfaError.set(apiErrorMessage(err));
      },
    });
  }

  protected changePassword(): void {
    if (this.passwordForm.invalid || this.pwdBusy()) {
      this.passwordForm.markAllAsTouched();
      return;
    }
    const { senhaAtual, novaSenha } = this.passwordForm.getRawValue();
    this.pwdBusy.set(true);
    this.pwdError.set(null);
    this.pwdDone.set(null);
    this.auth.changePassword(senhaAtual, novaSenha).subscribe({
      next: (r) => {
        this.pwdBusy.set(false);
        this.pwdDone.set(r.message);
        this.passwordForm.reset();
      },
      error: (err: unknown) => {
        this.pwdBusy.set(false);
        this.pwdError.set(apiErrorMessage(err));
      },
    });
  }

  protected logoutAll(): void {
    this.devicesError.set(null);
    this.auth.logoutAllDevices().subscribe({
      error: (err: unknown) => this.devicesError.set(apiErrorMessage(err)),
    });
  }
}
