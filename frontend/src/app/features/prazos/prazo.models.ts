export type StatusPrazo = 'ABERTO' | 'CUMPRIDO';
export type SituacaoPrazo = 'HOJE' | 'PROXIMO' | 'VENCIDO' | 'CUMPRIDO';

export const SITUACOES_PRAZO: { value: SituacaoPrazo; label: string; cssClass: string }[] = [
  { value: 'HOJE', label: 'Hoje', cssClass: 'chip--hoje' },
  { value: 'PROXIMO', label: 'Próximo', cssClass: 'chip--proximo' },
  { value: 'VENCIDO', label: 'Vencido', cssClass: 'chip--vencido' },
  { value: 'CUMPRIDO', label: 'Cumprido', cssClass: 'chip--cumprido' },
];

export const PERIODOS_PRAZO: { value: number | ''; label: string }[] = [
  { value: '', label: 'Sem limite' },
  { value: 0, label: 'Hoje' },
  { value: 7, label: 'Próximos 7 dias' },
  { value: 15, label: 'Próximos 15 dias' },
  { value: 30, label: 'Próximos 30 dias' },
];

export interface Prazo {
  id: number;
  processoId: number;
  processoNumero: string;
  clienteId: number;
  clienteNome: string;
  tipo: string;
  tipoRotulo: string;
  categoria: string;
  categoriaRotulo: string;
  descricao: string | null;
  /** Data (yyyy-MM-dd) do vencimento. */
  dataVencimento: string;
  status: StatusPrazo;
  situacao: SituacaoPrazo;
  responsavelId: number | null;
  responsavelNome: string | null;
  cumpridoEm: string | null;
  criadoEm: string;
}

export interface PrazoRequest {
  processoId: number;
  tipo: string;
  descricao?: string | null;
  dataVencimento: string;
  responsavelId?: number | null;
}

export interface PrazoResumo {
  hoje: number;
  proximos: number;
  vencidos: number;
  cumpridosUltimos30Dias: number;
}

/** Item do catálogo de tipos (vem do servidor: acrescentar tipo no backend não exige mudar a tela). */
export interface TipoPrazo {
  codigo: string;
  rotulo: string;
  categoria: string;
  categoriaRotulo: string;
}

export interface PrazoFiltro {
  situacao: SituacaoPrazo | '';
  dias: number | '';
  categoria: string;
  responsavelId: number | '';
  /** Só os prazos deste processo (usado na ficha do processo). */
  processoId?: number;
  /** Texto livre: cliente, número do processo, tipo de prazo ou descrição. */
  busca?: string;
  page: number;
  size: number;
}

/** yyyy-MM-dd → dd/MM/yyyy (sem passar por Date, para não deslocar o dia pelo fuso). */
export function dataBr(iso: string | null | undefined): string {
  if (!iso) return '—';
  const [a, m, d] = iso.split('-');
  return `${d}/${m}/${a}`;
}
