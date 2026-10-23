import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatIconModule } from '@angular/material/icon';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { filter, map } from 'rxjs';
import { NAV_ITEMS, NavItem } from '../../core/navigation/nav-items';

interface Crumb {
  label: string;
  route?: string;
}

/** Trilha de navegacao (Inicio > grupo > tela) montada a partir do menu em `nav-items.ts`. */
@Component({
  selector: 'app-breadcrumb',
  imports: [MatIconModule, RouterLink],
  templateUrl: './breadcrumb.html',
  styleUrl: './breadcrumb.scss',
})
export class Breadcrumb {
  private readonly router = inject(Router);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  protected readonly crumbs = computed<Crumb[]>(() => {
    const url = this.url().split(/[?#]/)[0];
    const trail = find(NAV_ITEMS, url, []);
    if (!trail?.length) return [];
    const crumbs: Crumb[] = trail.map((i) => ({ label: i.label, route: i.route }));
    // Subtelas de um item (".../novo", ".../:id/editar") ganham mais um nivel no fim da trilha.
    const resto = url.slice((trail[trail.length - 1].route ?? '').length);
    const sub = SUBTELAS.find((s) => resto.endsWith(s.sufixo));
    return sub ? [...crumbs, { label: sub.label }] : crumbs;
  });
}

const SUBTELAS = [
  { sufixo: '/novo', label: 'Novo' },
  { sufixo: '/editar', label: 'Editar' },
];

function find(items: NavItem[], url: string, parents: NavItem[]): NavItem[] | null {
  for (const item of items) {
    const path = [...parents, item];
    if (item.route && (url === item.route || url.startsWith(item.route + '/'))) return path;
    const inChildren = item.children && find(item.children, url, path);
    if (inChildren) return inChildren;
  }
  return null;
}
