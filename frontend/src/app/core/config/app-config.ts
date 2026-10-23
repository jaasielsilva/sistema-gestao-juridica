/** Configuracoes fixas da aplicacao. Troque ao iniciar um produto novo. */
export const APP_CONFIG = {
  name: 'Sistema de Gestão Jurídica',
  company: 'Sua Empresa',
  /** Prefixo de todas as chamadas a API. Em dev e proxied (proxy.conf.json); em producao, pelo nginx. */
  apiBase: '/api/v1',
} as const;
