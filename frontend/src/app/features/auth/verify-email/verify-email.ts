import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { apiErrorMessage } from '../../../core/auth/api-error';
import { AuthService } from '../../../core/auth/auth.service';
import { AuthPage } from '../../../shared/auth-page/auth-page';

@Component({
  selector: 'app-verify-email',
  imports: [RouterLink, MatButtonModule, MatProgressSpinnerModule, AuthPage],
  templateUrl: './verify-email.html',
})
export class VerifyEmail implements OnInit {
  private readonly auth = inject(AuthService);

  /** Vem de ?token= na URL (withComponentInputBinding). */
  readonly token = input<string>();

  protected readonly state = signal<'loading' | 'ok' | 'error'>('loading');
  protected readonly message = signal('');

  ngOnInit(): void {
    const token = this.token();
    if (!token) {
      this.state.set('error');
      this.message.set('Link inválido.');
      return;
    }
    this.auth.verifyEmail(token).subscribe({
      next: (r) => {
        this.message.set(r.message);
        this.state.set('ok');
      },
      error: (err: unknown) => {
        this.message.set(apiErrorMessage(err));
        this.state.set('error');
      },
    });
  }
}
