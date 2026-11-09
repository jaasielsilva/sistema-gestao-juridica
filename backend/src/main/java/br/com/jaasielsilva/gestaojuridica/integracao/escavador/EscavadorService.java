package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import br.com.jaasielsilva.gestaojuridica.audit.AuditService;
import br.com.jaasielsilva.gestaojuridica.audit.TipoEvento;
import br.com.jaasielsilva.gestaojuridica.config.AppProperties;
import br.com.jaasielsilva.gestaojuridica.processo.Processo;
import br.com.jaasielsilva.gestaojuridica.processo.ProcessoRepository;
import br.com.jaasielsilva.gestaojuridica.security.AuthPrincipal;
import br.com.jaasielsilva.gestaojuridica.shared.BusinessException;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regra de negocio da integracao com o Escavador. Cada tenant usa a propria credencial (ver ADR
 * 0007): nenhum metodo aqui aceita ou depende de um token global da plataforma.
 */
@Service
public class EscavadorService {

    /** Custo mensal por frequencia, em centavos (tabela de precos real da conta, consultada em 2026-11-08). */
    private static final Map<FrequenciaMonitoramento, Long> CUSTO_MENSAL_CENTAVOS = new EnumMap<>(Map.of(
            FrequenciaMonitoramento.MENSAL, 8L,
            FrequenciaMonitoramento.SEMANAL, 32L,
            FrequenciaMonitoramento.DIARIA, 176L));

    private final EscavadorConfiguracaoRepository configuracoes;
    private final MovimentacaoRepository movimentacoes;
    private final ProcessoRepository processos;
    private final EscavadorClient client;
    private final EscavadorCipher cipher;
    private final AuditService audit;
    private final AppProperties props;

    public EscavadorService(EscavadorConfiguracaoRepository configuracoes, MovimentacaoRepository movimentacoes,
                            ProcessoRepository processos, EscavadorClient client, EscavadorCipher cipher,
                            AuditService audit, AppProperties props) {
        this.configuracoes = configuracoes;
        this.movimentacoes = movimentacoes;
        this.processos = processos;
        this.client = client;
        this.cipher = cipher;
        this.audit = audit;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public EscavadorConfiguracaoResponse obterConfiguracao() {
        Optional<EscavadorConfiguracao> cfg = configuracaoAtual();
        if (cfg.isEmpty()) {
            return EscavadorConfiguracaoResponse.naoConfigurado();
        }
        EscavadorConfiguracao c = cfg.get();
        return new EscavadorConfiguracaoResponse(true, c.isHabilitado(), c.getSaldoLimiteCentavos(),
                gastoMensalAtualCentavos(), urlCallback(c.getCallbackSlug()));
    }

    @Transactional
    public EscavadorConfiguracaoResponse salvarConfiguracao(EscavadorConfiguracaoRequest req, AuthPrincipal quem) {
        Optional<EscavadorConfiguracao> existente = configuracaoAtual();
        String tokenCifrado = cipher.encrypt(req.token());
        String callbackSecretCifrado = cipher.encrypt(req.callbackSecret());
        EscavadorConfiguracao cfg;
        if (existente.isPresent()) {
            cfg = existente.get();
            cfg.atualizarCredenciais(tokenCifrado, callbackSecretCifrado);
            cfg.definirSaldoLimiteCentavos(req.saldoLimiteCentavos());
        } else {
            cfg = configuracoes.save(new EscavadorConfiguracao(tokenCifrado, callbackSecretCifrado, req.saldoLimiteCentavos()));
        }
        audit.sucesso(TipoEvento.ESCAVADOR_CONFIGURACAO_SALVA, quem.userId(), quem.tenantId(), null);
        return obterConfiguracao();
    }

    @Transactional
    public void ativarMonitoramento(Long processoId, FrequenciaMonitoramento frequencia, AuthPrincipal quem) {
        // Existencia/isolamento do processo primeiro: nunca revelar estado de configuracao antes de
        // confirmar que o recurso pertence ao tenant do chamador.
        Processo processo = processos.findById(processoId).orElseThrow(() -> BusinessException.notFound("Processo nao encontrado"));
        EscavadorConfiguracao cfg = configuracaoHabilitadaOuFalhar();

        long custoNovo = CUSTO_MENSAL_CENTAVOS.get(frequencia);
        long custoAtualDoProcesso = processo.getFrequenciaMonitoramento() == null
                ? 0
                : CUSTO_MENSAL_CENTAVOS.get(processo.getFrequenciaMonitoramento());
        long projecao = gastoMensalAtualCentavos() - custoAtualDoProcesso + custoNovo;
        if (projecao > cfg.getSaldoLimiteCentavos()) {
            throw BusinessException.conflict(
                    "Ativar esse monitoramento ultrapassa o limite mensal configurado para a integracao Escavador");
        }

        String token = cipher.decrypt(cfg.getTokenCifrado());
        if (processo.getMonitoramentoEscavadorId() != null) {
            // Troca de frequencia de um monitoramento ja ativo: edita em vez de recriar.
            client.editarFrequencia(token, processo.getMonitoramentoEscavadorId(), frequencia);
            processo.ativarMonitoramento(processo.getMonitoramentoEscavadorId(), frequencia);
        } else {
            EscavadorClient.MonitoramentoResposta resposta = client.criarMonitoramento(token, processo.getNumero(), frequencia);
            processo.ativarMonitoramento(resposta.id(), frequencia);
        }
        audit.sucesso(TipoEvento.ESCAVADOR_MONITORAMENTO_ATIVADO, quem.userId(), quem.tenantId(),
                "processoId=" + processoId + " frequencia=" + frequencia);
    }

    @Transactional
    public void desativarMonitoramento(Long processoId, AuthPrincipal quem) {
        Processo processo = processos.findById(processoId).orElseThrow(() -> BusinessException.notFound("Processo nao encontrado"));
        EscavadorConfiguracao cfg = configuracaoHabilitadaOuFalhar();
        if (processo.getMonitoramentoEscavadorId() == null) {
            return;
        }
        String token = cipher.decrypt(cfg.getTokenCifrado());
        client.removerMonitoramento(token, processo.getMonitoramentoEscavadorId());
        processo.desativarMonitoramento();
        audit.sucesso(TipoEvento.ESCAVADOR_MONITORAMENTO_DESATIVADO, quem.userId(), quem.tenantId(), "processoId=" + processoId);
    }

    @Transactional(readOnly = true)
    public Page<MovimentacaoResponse> listarMovimentacoes(Long processoId, Pageable pageable) {
        processos.findById(processoId).orElseThrow(() -> BusinessException.notFound("Processo nao encontrado"));
        return movimentacoes.findByProcessoIdOrderByDataMovimentoDesc(processoId, pageable).map(MovimentacaoResponse::of);
    }

    /**
     * Processa um callback ja identificado e autenticado (ver EscavadorCallbackController). Assume que o
     * TenantContext ja foi fixado para o tenant certo antes desta chamada.
     */
    @Transactional
    public void registrarMovimentacao(String numeroCnj, Instant dataMovimento, String tipoEvento, String descricao,
                                      String hashDedup) {
        Optional<Processo> processo = processos.findByNumero(numeroCnj);
        if (processo.isEmpty()) {
            return; // processo nao cadastrado (ou de outro tenant, mas o tenant ja esta fixado) — nada a gravar
        }
        if (movimentacoes.existsByProcessoIdAndHashDedup(processo.get().getId(), hashDedup)) {
            return; // reentrega do mesmo evento — idempotente
        }
        movimentacoes.save(new Movimentacao(processo.get(), dataMovimento, tipoEvento, descricao, hashDedup));
    }

    /** Compara em tempo constante; usado pelo controller de callback ANTES de fixar o TenantContext. */
    public boolean validarCallbackSecret(String secretCifrado, String recebido) {
        if (recebido == null) {
            return false;
        }
        String esperado = cipher.decrypt(secretCifrado);
        return MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), recebido.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<EscavadorConfiguracao> configuracaoAtual() {
        return configuracoes.findAll().stream().findFirst();
    }

    private EscavadorConfiguracao configuracaoHabilitadaOuFalhar() {
        EscavadorConfiguracao cfg = configuracaoAtual()
                .orElseThrow(() -> BusinessException.conflict("Integracao Escavador nao configurada para este escritorio"));
        if (!cfg.isHabilitado()) {
            throw BusinessException.conflict("Integracao Escavador desabilitada para este escritorio");
        }
        return cfg;
    }

    private long gastoMensalAtualCentavos() {
        return processos.contarPorFrequenciaMonitoramento().stream()
                .mapToLong(row -> CUSTO_MENSAL_CENTAVOS.get((FrequenciaMonitoramento) row[0]) * (Long) row[1])
                .sum();
    }

    private String urlCallback(UUID slug) {
        return props.baseUrl() + "/api/v1/integracoes/escavador/callback/" + slug;
    }
}
