export type TipoPessoa = 'PF' | 'PJ';

export interface Cliente {
  id: number;
  tipo: TipoPessoa;
  nome: string;
  documento: string;
  email: string | null;
  telefone: string | null;
  cep: string | null;
  logradouro: string | null;
  numero: string | null;
  complemento: string | null;
  bairro: string | null;
  cidade: string | null;
  uf: string | null;
  observacoes: string | null;
  ativo: boolean;
  criadoEm: string;
  atualizadoEm: string;
}

/** Corpo de cadastro/edicao (CPF/CNPJ, CEP e telefone podem ir com mascara; o servidor normaliza). */
export type ClienteRequest = Omit<Cliente, 'id' | 'ativo' | 'criadoEm' | 'atualizadoEm'>;

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ClienteResumo {
  total: number;
  ativos: number;
  inativos: number;
  pessoasFisicas: number;
  pessoasJuridicas: number;
}

export interface ClienteFiltro {
  busca: string;
  tipo: TipoPessoa | '';
  /** 'todos' nao filtra por status. */
  status: 'ativos' | 'inativos' | 'todos';
  cidade: string;
  uf: string;
  page: number;
  size: number;
}

export const UFS = [
  'AC',
  'AL',
  'AP',
  'AM',
  'BA',
  'CE',
  'DF',
  'ES',
  'GO',
  'MA',
  'MT',
  'MS',
  'MG',
  'PA',
  'PB',
  'PR',
  'PE',
  'PI',
  'RJ',
  'RN',
  'RS',
  'RO',
  'RR',
  'SC',
  'SP',
  'SE',
  'TO',
] as const;
