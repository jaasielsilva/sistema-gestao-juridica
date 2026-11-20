export type StatusTarefa = 'A_FAZER' | 'EM_ANDAMENTO' | 'AGUARDANDO' | 'CONCLUIDA';
export type PrioridadeTarefa = 'BAIXA' | 'MEDIA' | 'ALTA' | 'URGENTE';

/** Colunas do quadro, na ordem de exibição. */
export const COLUNAS_TAREFA: { value: StatusTarefa; label: string; icon: string }[] = [
  { value: 'A_FAZER', label: 'A fazer', icon: 'radio_button_unchecked' },
  { value: 'EM_ANDAMENTO', label: 'Em andamento', icon: 'autorenew' },
  { value: 'AGUARDANDO', label: 'Aguardando', icon: 'hourglass_empty' },
  { value: 'CONCLUIDA', label: 'Concluídas', icon: 'check_circle' },
];

export const PRIORIDADES_TAREFA: { value: PrioridadeTarefa; label: string }[] = [
  { value: 'BAIXA', label: 'Baixa' },
  { value: 'MEDIA', label: 'Média' },
  { value: 'ALTA', label: 'Alta' },
  { value: 'URGENTE', label: 'Urgente' },
];

export interface Tarefa {
  id: number;
  titulo: string;
  descricao: string | null;
  status: StatusTarefa;
  prioridade: PrioridadeTarefa;
  /** Data (yyyy-MM-dd) ou nulo. */
  prazo: string | null;
  atrasada: boolean;
  processoId: number | null;
  processoNumero: string | null;
  clienteNome: string | null;
  responsavelId: number | null;
  responsavelNome: string | null;
  ordem: number;
  /** Quem criou: só essa pessoa ou um ADMIN pode excluir. */
  criadoPorId: number;
  concluidaEm: string | null;
  criadoEm: string;
}

export interface TarefaRequest {
  titulo: string;
  descricao?: string | null;
  prioridade: PrioridadeTarefa;
  prazo?: string | null;
  processoId?: number | null;
  responsavelId?: number | null;
  status?: StatusTarefa | null;
}

/** Agrupa as tarefas do quadro por coluna, preservando a ordem do servidor (concluídas: mais recentes primeiro). */
export function agruparPorColuna(tarefas: Tarefa[]): Record<StatusTarefa, Tarefa[]> {
  const colunas: Record<StatusTarefa, Tarefa[]> = { A_FAZER: [], EM_ANDAMENTO: [], AGUARDANDO: [], CONCLUIDA: [] };
  for (const t of tarefas) colunas[t.status].push(t);
  colunas.CONCLUIDA.sort((a, b) => (b.concluidaEm ?? '').localeCompare(a.concluidaEm ?? ''));
  return colunas;
}
