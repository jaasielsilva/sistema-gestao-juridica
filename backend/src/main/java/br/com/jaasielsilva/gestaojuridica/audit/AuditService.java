package br.com.jaasielsilva.gestaojuridica.audit;

import br.com.jaasielsilva.gestaojuridica.shared.RequestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Grava eventos de seguranca em transacao propria: falhas (login errado, token reutilizado) ficam registradas
 * mesmo quando a requisicao termina em erro. Uma falha ao auditar NAO derruba a operacao, mas e logada como erro.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final EventoAuditoriaRepository repo;
    private final RequestInfo request;
    private final TransactionTemplate tx;

    public AuditService(EventoAuditoriaRepository repo, RequestInfo request, PlatformTransactionManager tm) {
        this.repo = repo;
        this.request = request;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void sucesso(TipoEvento tipo, Long usuarioId, Long tenantId, String detalhe) {
        record(tipo, Resultado.SUCESSO, usuarioId, tenantId, null, detalhe);
    }

    public void falha(TipoEvento tipo, Long usuarioId, Long tenantId, String emailInformado, String detalhe) {
        record(tipo, Resultado.FALHA, usuarioId, tenantId, emailInformado, detalhe);
    }

    private void record(TipoEvento tipo, Resultado resultado, Long usuarioId, Long tenantId,
                        String emailInformado, String detalhe) {
        try {
            var evento = new EventoAuditoria(tipo, resultado, usuarioId, tenantId, emailInformado,
                    request.ip(), request.correlationId(), detalhe);
            tx.executeWithoutResult(s -> repo.save(evento));
        } catch (RuntimeException ex) {
            log.error("Falha ao gravar evento de auditoria {} ({})", tipo, resultado, ex);
        }
    }
}
