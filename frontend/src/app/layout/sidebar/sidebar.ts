import { Component, computed, inject, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { NAV_ITEMS } from '../../core/navigation/nav-items';

@Component({
  selector: 'app-sidebar',
  imports: [MatListModule, MatIconModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.html',
  styleUrl: './sidebar.scss',
})
export class Sidebar {
  private readonly auth = inject(AuthService);

  /** Itens visiveis para o papel do usuario atual (apenas conveniencia de UX). */
  protected readonly items = computed(() => {
    const papel = this.auth.user()?.papel;
    return NAV_ITEMS.filter((i) => !i.roles || (papel !== undefined && i.roles.includes(papel)));
  });

  readonly navigated = output<void>();
}
