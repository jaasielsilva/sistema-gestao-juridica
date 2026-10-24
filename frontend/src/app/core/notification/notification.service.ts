import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

/**
 * Servico global de notificacoes toast.
 * Usa MatSnackBar com classes CSS globais definidas em styles.scss:
 *   .snack-success  — fundo verde
 *   .snack-error    — fundo vermelho
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly snack = inject(MatSnackBar);

  success(message: string, duration = 3500): void {
    this.snack.open(message, 'Fechar', {
      duration,
      panelClass: ['snack-success'],
      horizontalPosition: 'right',
      verticalPosition: 'top',
    });
  }

  error(message: string, duration = 5000): void {
    this.snack.open(message, 'Fechar', {
      duration,
      panelClass: ['snack-error'],
      horizontalPosition: 'right',
      verticalPosition: 'top',
    });
  }
}
