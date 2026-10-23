import { Component, ViewEncapsulation, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { APP_CONFIG } from '../../core/config/app-config';

@Component({
  selector: 'app-branded-auth-page',
  imports: [MatIconModule],
  templateUrl: './branded-auth-page.html',
  styleUrl: './branded-auth-page.scss',
  encapsulation: ViewEncapsulation.None,
})
export class BrandedAuthPage {
  readonly heading = input.required<string>();
  readonly description = input.required<string>();

  protected readonly appName = APP_CONFIG.name;
}
