import { Component, computed, inject, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ThemeService } from '../../core/theme/theme.service';

/** Barra superior da área logada: busca global, notificações com contador, alternador de tema e perfil do usuário. */
@Component({
  selector: 'app-header',
  imports: [MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './header.html',
  styleUrl: './header.scss',
})
export class Header {
  protected readonly theme = inject(ThemeService);
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly menuToggle = output<void>();

  protected readonly initials = computed(() => {
    const nome = this.auth.user()?.nome?.trim();
    if (!nome) return 'JM';
    const parts = nome.split(/\s+/);
    return ((parts[0][0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  });

  protected readonly papelLabel = computed(() =>
    this.auth.user()?.papel === 'ADMIN' ? 'Administrador' : 'Usuário',
  );

  protected irParaBusca(event: Event): void {
    const input = event.target as HTMLInputElement;
    const termo = input.value.trim();
    if (termo) {
      void this.router.navigate(['/processos'], { queryParams: { busca: termo } });
    }
  }
}
