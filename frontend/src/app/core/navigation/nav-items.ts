import { Papel } from '../auth/auth.models';

export interface NavItem {
  label: string;
  /** Nome do icone Material Symbols. */
  icon: string;
  /** Rota do item. Grupos (com `children`) nao tem rota propria. */
  route?: string;
  /** Se informado, o item so aparece para estes papeis (a protecao real e no backend). */
  roles?: Papel[];
  /** Subitens: o item vira um grupo expansivel no menu. */
  children?: NavItem[];
  /** Fixa o item no fim do menu, logo acima do card do usuario. */
  bottom?: boolean;
}

/** Menu lateral. Cada produto novo adiciona seus itens aqui, acima de "Minha conta" (fixa no fim). */
export const NAV_ITEMS: NavItem[] = [
  { label: 'Início', icon: 'home', route: '/inicio' },
  { label: 'Clientes', icon: 'groups', route: '/clientes' },
  // Exemplo: { label: 'Usuários', icon: 'group', route: '/usuarios', roles: ['ADMIN'] },
  {
    label: 'Minha conta',
    icon: 'manage_accounts',
    bottom: true,
    children: [{ label: 'Segurança', icon: 'security', route: '/seguranca' }],
  },
];
