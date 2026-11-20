import { Component, DestroyRef, computed, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router } from '@angular/router';
import { catchError, filter, merge, of, switchMap, timer } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ThemeService } from '../../core/theme/theme.service';
import { PrazoResumo } from '../../features/prazos/prazo.models';
import { PrazoService } from '../../features/prazos/prazo.service';

/** De quanto em quanto tempo o contador de prazos do sino é atualizado enquanto a tela fica aberta. */
const ATUALIZAR_PRAZOS_MS = 5 * 60 * 1000;

/** Barra superior da área logada: alerta de prazos, alternador de tema e perfil do usuário. */
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
  private readonly prazos = inject(PrazoService);
  readonly menuToggle = output<void>();

  private readonly resumoPrazos = signal<PrazoResumo | null>(null);

  /** Prazos que pedem ação agora: os que vencem hoje mais os já vencidos. */
  protected readonly alertas = computed(() => {
    const r = this.resumoPrazos();
    return r ? r.hoje + r.vencidos : 0;
  });

  protected readonly alertasTexto = computed(() => {
    const r = this.resumoPrazos();
    if (!r || this.alertas() === 0) return 'Nenhum prazo vencido ou vencendo hoje';
    return `${r.vencidos} prazo(s) vencido(s) e ${r.hoje} vencendo hoje`;
  });

  constructor() {
    // Atualiza ao abrir, a cada 5 minutos e a cada navegação (para refletir um prazo baixado na tela anterior).
    merge(
      timer(0, ATUALIZAR_PRAZOS_MS),
      this.router.events.pipe(filter((e) => e instanceof NavigationEnd)),
    )
      .pipe(
        switchMap(() => this.prazos.resumo().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(inject(DestroyRef)),
      )
      .subscribe((r) => {
        if (r) this.resumoPrazos.set(r);
      });
  }

  protected abrirPrazos(): void {
    void this.router.navigateByUrl('/prazos');
  }

  protected readonly initials = computed(() => {
    const nome = this.auth.user()?.nome?.trim();
    if (!nome) return 'JM';
    const parts = nome.split(/\s+/);
    return ((parts[0][0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  });

  protected readonly papelLabel = computed(() =>
    this.auth.user()?.papel === 'ADMIN' ? 'Administrador' : 'Usuário',
  );
}
