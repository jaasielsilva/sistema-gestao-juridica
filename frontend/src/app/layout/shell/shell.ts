import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnInit, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatSidenavModule } from '@angular/material/sidenav';
import { RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Footer } from '../footer/footer';
import { Header } from '../header/header';
import { Sidebar } from '../sidebar/sidebar';

/** Estrutura da area logada: cabecalho no topo, menu lateral, conteudo rolavel e rodape fixo embaixo. */
@Component({
  selector: 'app-shell',
  imports: [MatSidenavModule, RouterOutlet, Header, Sidebar, Footer],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell implements OnInit {
  private readonly auth = inject(AuthService);

  protected readonly isMobile = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 899.98px)')
      .pipe(map((r) => r.matches)),
    { initialValue: false },
  );

  ngOnInit(): void {
    // Apos recarregar a pagina o token existe, mas o usuario precisa ser buscado de novo.
    if (!this.auth.user()) this.auth.loadUser().subscribe({ error: () => undefined });
  }
}
