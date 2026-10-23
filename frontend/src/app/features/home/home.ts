import { Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-home',
  imports: [MatCardModule, MatIconModule],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home {
  protected readonly auth = inject(AuthService);

  protected readonly benefits = [
    { icon: 'description', title: 'Organização', text: 'Informações em ordem' },
    { icon: 'event', title: 'Agilidade', text: 'Uma rotina mais simples' },
    { icon: 'groups', title: 'Equipe', text: 'Acesso para sua equipe' },
    { icon: 'verified_user', title: 'Segurança', text: 'Proteção da sua conta' },
  ];
}
