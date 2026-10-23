import { Component, computed, inject, output, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { filter, map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { APP_CONFIG } from '../../core/config/app-config';
import { NAV_ITEMS, NavItem } from '../../core/navigation/nav-items';

@Component({
  selector: 'app-sidebar',
  imports: [MatIconModule, MatMenuModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly appName = APP_CONFIG.name;

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Grupos abertos/fechados manualmente; sem escolha do usuario, abre o grupo da rota atual. */
  private readonly toggled = signal<Record<string, boolean>>({});

  /** Itens visiveis para o papel do usuario atual (apenas conveniencia de UX). */
  protected readonly items = computed(() => {
    const papel = this.auth.user()?.papel;
    const allowed = (i: NavItem) => !i.roles || (papel !== undefined && i.roles.includes(papel));
    return NAV_ITEMS.filter(allowed).map((i) => (i.children ? { ...i, children: i.children.filter(allowed) } : i));
  });

  protected readonly initials = computed(() => {
    const nome = this.auth.user()?.nome?.trim();
    if (!nome) return '?';
    const parts = nome.split(/\s+/);
    return ((parts[0][0] ?? '') + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
  });

  protected readonly papelLabel = computed(() => (this.auth.user()?.papel === 'ADMIN' ? 'Administrador' : 'Usuário'));

  readonly navigated = output<void>();

  protected isOpen(item: NavItem): boolean {
    return this.toggled()[item.label] ?? this.hasActiveChild(item);
  }

  protected toggle(item: NavItem): void {
    const open = this.isOpen(item);
    this.toggled.update((t) => ({ ...t, [item.label]: !open }));
  }

  private hasActiveChild(item: NavItem): boolean {
    const url = this.url().split(/[?#]/)[0];
    return !!item.children?.some((c) => c.route && (url === c.route || url.startsWith(c.route + '/')));
  }
}
