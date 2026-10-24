import { TipoPessoa } from '../clientes/cliente.models';

export type TipoProcesso =
  | 'CIVIL'
  | 'TRABALHISTA'
  | 'EMPRESARIAL'
  | 'FAMILIA'
  | 'CRIMINAL'
  | 'ADMINISTRATIVO'
  | 'BANCARIO';

export const TIPOS_PROCESSO: { value: TipoProcesso; label: string }[] = [
  { value: 'CIVIL', label: 'Cível' },
  { value: 'TRABALHISTA', label: 'Trabalhista' },
  { value: 'EMPRESARIAL', label: 'Empresarial' },
  { value: 'FAMILIA', label: 'Família' },
  { value: 'CRIMINAL', label: 'Criminal' },
  { value: 'ADMINISTRATIVO', label: 'Administrativo' },
  { value: 'BANCARIO', label: 'Bancário' },
];

export type SituacaoProcesso =
  | 'EM_ANDAMENTO'
  | 'PENDENTE'
  | 'SUSPENSO'
  | 'ENCERRADO'
  | 'URGENTE';

export const SITUACOES_PROCESSO: {
  value: SituacaoProcesso;
  label: string;
  cssClass: string;
}[] = [
  { value: 'EM_ANDAMENTO', label: 'Em andamento', cssClass: 'badge--em-andamento' },
  { value: 'PENDENTE', label: 'Pendente', cssClass: 'badge--pendente' },
  { value: 'SUSPENSO', label: 'Suspenso', cssClass: 'badge--suspenso' },
  { value: 'ENCERRADO', label: 'Encerrado', cssClass: 'badge--encerrado' },
  { value: 'URGENTE', label: 'Urgente', cssClass: 'badge--urgente' },
];

export interface Processo {
  id: number;
  numero: string;
  clienteId: number;
  clienteNome: string;
  clienteDocumento: string;
  clienteTipo: TipoPessoa;
  tipo: TipoProcesso;
  areaJuridica: string;
  tribunal: string;
  comarca: string | null;
  vara: string | null;
  advogadoNome: string;
  advogadoOab: string | null;
  situacao: SituacaoProcesso;
  proximoPrazo: string | null;
  proximoPrazoDescricao: string | null;
  observacoes: string | null;
  criadoEm: string;
  atualizadoEm: string;
}

export interface ProcessoRequest {
  numero: string;
  clienteId: number;
  tipo: TipoProcesso;
  areaJuridica: string;
  tribunal: string;
  comarca?: string | null;
  vara?: string | null;
  advogadoNome: string;
  advogadoOab?: string | null;
  situacao: SituacaoProcesso;
  proximoPrazo?: string | null;
  proximoPrazoDescricao?: string | null;
  observacoes?: string | null;
}

export interface ProcessoResumo {
  total: number;
  emAndamento: number;
  suspensos: number;
  encerrados: number;
  urgentes: number;
}

export interface ProcessoFiltro {
  busca: string;
  clienteId?: number | null;
  tipo: TipoProcesso | '';
  areaJuridica: string;
  tribunal: string;
  situacao: SituacaoProcesso | '';
  page: number;
  size: number;
}

export const AREAS_JURIDICAS = [
  'Direito Civil',
  'Direito do Trabalho',
  'Direito Empresarial',
  'Direito de Família',
  'Direito Penal',
  'Direito Administrativo',
  'Direito do Consumidor',
  'Direito Tributário',
  'Direito Previdenciário',
  'Direito Imobiliário',
] as const;

export const TRIBUNAIS = [
  'TJSP - Comarca de São Paulo',
  'TJSP - Comarca de Campinas',
  'TJSP - Comarca de Santo André',
  'TJSP - Comarca de Sorocaba',
  'TJSP - Comarca de Ribeirão Preto',
  'TJSP - Comarca de Osasco',
  'TJSP - Comarca de Guarulhos',
  'TRT 2ª Região',
  'TRT 15ª Região',
  'TRF 3ª Região',
] as const;
