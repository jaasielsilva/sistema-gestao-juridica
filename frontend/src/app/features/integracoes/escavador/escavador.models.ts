export type FrequenciaMonitoramento = 'MENSAL' | 'SEMANAL' | 'DIARIA';

export const FREQUENCIAS_MONITORAMENTO: { value: FrequenciaMonitoramento; label: string; custoMensal: string }[] = [
  { value: 'MENSAL', label: 'Mensal', custoMensal: 'R$ 0,08/mês' },
  { value: 'SEMANAL', label: 'Semanal', custoMensal: 'R$ 0,32/mês' },
  { value: 'DIARIA', label: 'Diária', custoMensal: 'R$ 1,76/mês' },
];

export interface EscavadorConfiguracao {
  configurado: boolean;
  habilitado: boolean;
  saldoLimiteCentavos: number;
  gastoMensalEstimadoCentavos: number;
  urlCallback: string | null;
}

export interface EscavadorConfiguracaoRequest {
  token: string;
  callbackSecret: string;
  saldoLimiteCentavos: number;
}

export interface Movimentacao {
  id: number;
  dataMovimento: string;
  tipoEvento: string;
  descricao: string;
  origem: string;
}
