import { Papel } from '../auth/auth.models';

export interface NavItem {
  label: string;
  /** Nome do icone Material Symbols. */
  icon: string;
  route: string;
  /** Se informado, o item so aparece para estes papeis (a protecao real e no backend). */
  roles?: Papel[];
}

/** Menu lateral. Cada produto novo adiciona seus itens aqui. */
export const NAV_ITEMS: NavItem[] = [
  { label: 'Início', icon: 'home', route: '/inicio' },
  // Exemplo: { label: 'Usuários', icon: 'group', route: '/usuarios', roles: ['ADMIN'] },
];
