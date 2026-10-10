import { Component } from '@angular/core';
import { APP_CONFIG } from '../../core/config/app-config';

@Component({
  selector: 'app-footer',
  templateUrl: './footer.html',
  styleUrl: './footer.scss',
})
export class Footer {
  protected readonly year = new Date().getFullYear();
  protected readonly appName = APP_CONFIG.name;
  protected readonly company = APP_CONFIG.company;
}
