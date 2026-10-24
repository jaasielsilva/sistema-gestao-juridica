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

/** Menu lateral. Conforme o padrão do sistema e protótipo visual. */
export const NAV_ITEMS: NavItem[] = [
  { label: 'Início', icon: 'home', route: '/inicio' },
  { label: 'Clientes', icon: 'groups', route: '/clientes' },
  {
    label: 'Processos',
    icon: 'folder',
    children: [
      { label: 'Listar Processos', icon: 'format_list_bulleted', route: '/processos' },
      { label: 'Novo Processo', icon: 'add_box', route: '/processos/novo' },
    ],
  },
  {
    label: 'Minha conta',
    icon: 'manage_accounts',
    bottom: true,
    children: [{ label: 'Segurança', icon: 'security', route: '/seguranca' }],
  },
];
