import { EtapaProcesso } from '../processos/processo.models';

export type RadarFaixa = 'OK' | 'ATENCAO' | 'TRAVADO';

export interface RadarItem {
  id: number;
  numero: string;
  clienteId: number;
  clienteNome: string;
  tipo: string;
  areaJuridica: string;
  situacao: string;
  advogadoNome: string;
  etapa: EtapaProcesso;
  etapaRotulo: string;
  etapaAtualizadaEm: string;
  diasNaEtapa: number;
  faixa: RadarFaixa;
}

export interface RadarResumo {
  total: number;
  travados: number;
  atencao: number;
  fluindo: number;
  porEtapa: Record<EtapaProcesso, number>;
}

export const FAIXAS_RADAR: { value: RadarFaixa; label: string; cssClass: string }[] = [
  { value: 'TRAVADO', label: 'Travado', cssClass: 'faixa--travado' },
  { value: 'ATENCAO', label: 'Atenção', cssClass: 'faixa--atencao' },
  { value: 'OK', label: 'Fluindo', cssClass: 'faixa--ok' },
];
