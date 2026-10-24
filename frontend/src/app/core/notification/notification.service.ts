import { Injectable } from '@angular/core';
import Swal from 'sweetalert2';

/**
 * Servico global de notificacoes toast.
 * Usa SweetAlert2 em modo toast (canto superior direito, auto-dismiss,
 * barra de progresso do timer, botao de fechar).
 */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  /** Configuracao base reaproveitada por todos os toasts. */
  private readonly toast = Swal.mixin({
    toast: true,
    position: 'top-end',
    showConfirmButton: false,
    showCloseButton: true,
    timerProgressBar: true,
    timer: 3500,
    didOpen: (el) => {
      el.addEventListener('mouseenter', Swal.stopTimer);
      el.addEventListener('mouseleave', Swal.resumeTimer);
    },
  });

  success(message: string, duration = 3500): void {
    this.toast.fire({ icon: 'success', title: message, timer: duration });
  }

  error(message: string, duration = 5000): void {
    this.toast.fire({ icon: 'error', title: message, timer: duration });
  }

  info(message: string, duration = 4000): void {
    this.toast.fire({ icon: 'info', title: message, timer: duration });
  }
}
