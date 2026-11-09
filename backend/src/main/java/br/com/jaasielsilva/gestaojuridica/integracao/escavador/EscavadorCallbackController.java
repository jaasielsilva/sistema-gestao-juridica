package br.com.jaasielsilva.gestaojuridica.integracao.escavador;

import br.com.jaasielsilva.gestaojuridica.audit.AuditService;
import br.com.jaasielsilva.gestaojuridica.audit.TipoEvento;
import br.com.jaasielsilva.gestaojuridica.shared.Hashing;
import br.com.jaasielsilva.gestaojuridica.tenant.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Recebe os callbacks do Escavador (monitoramento e atualizacao de processo). Rota PUBLICA (ver
 * SecurityConfig): quem chama e o servidor do Escavador, nao um usuario logado — por isso a
 * autenticacao aqui e o segredo de callback por tenant, nao um JWT.
 *
 * IMPORTANTE: o formato exato do payload ainda nao foi observado em produção (so a doc oficial e um
 * teste manual via curl que criou o monitoramento, sem aguardar o primeiro evento real). O parser
 * abaixo e deliberadamente tolerante (tenta varios nomes de campo plausiveis) e loga o payload bruto
 * (sem dado sensivel) para permitir ajuste fino quando o primeiro callback real chegar.
 */
@RestController
@RequestMapping("/api/v1/integracoes/escavador/callback")
public class EscavadorCallbackController {

    private static final Logger log = LoggerFactory.getLogger(EscavadorCallbackController.class);

    private final EscavadorCallbackLookup lookup;
    private final EscavadorService service;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public EscavadorCallbackController(EscavadorCallbackLookup lookup, EscavadorService service, AuditService audit,
                                       ObjectMapper mapper) {
        this.lookup = lookup;
        this.service = service;
        this.audit = audit;
        this.mapper = mapper;
    }

    @PostMapping("/{slug}")
    public ResponseEntity<Void> receber(@PathVariable UUID slug,
                                        @RequestHeader(value = "Authorization", required = false) String authorization,
                                        @RequestBody String rawBody) {
        var alvo = lookup.buscarPorSlug(slug);
        if (alvo.isEmpty() || !alvo.get().habilitado()) {
            // Mesma resposta para "slug inexistente" e "desabilitado": nao revela qual dos dois.
            return ResponseEntity.notFound().build();
        }

        String recebido = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : authorization;
        if (!service.validarCallbackSecret(alvo.get().callbackSecretCifrado(), recebido)) {
            audit.falha(TipoEvento.ESCAVADOR_CALLBACK_REJEITADO, null, alvo.get().tenantId(), null,
                    "callback secret invalido");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            TenantContext.set(alvo.get().tenantId());
            processar(rawBody);
            audit.sucesso(TipoEvento.ESCAVADOR_CALLBACK_RECEBIDO, null, alvo.get().tenantId(), resumoParaLog(rawBody));
        } finally {
            TenantContext.clear();
        }
        return ResponseEntity.ok().build();
    }

    private void processar(String rawBody) {
        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (Exception ex) {
            log.warn("Callback do Escavador com corpo ilegivel: {}", ex.getMessage());
            return;
        }

        String numeroCnj = texto(root, "numero_cnj", "numeroCnj");
        if (numeroCnj == null) {
            JsonNode processoNode = root.get("processo");
            if (processoNode != null) {
                numeroCnj = texto(processoNode, "numero_cnj", "numeroCnj", "numero");
            }
        }
        if (numeroCnj == null) {
            log.warn("Callback do Escavador sem numero_cnj identificavel — payload ignorado, ver log para ajustar o parser");
            return;
        }

        String evento = texto(root, "evento", "event", "tipo");
        String descricao = texto(root, "descricao", "mensagem", "resumo");
        if (descricao == null) {
            descricao = evento != null ? evento : "Atualizacao recebida do Escavador";
        }

        Instant data = Instant.now();
        String dataTexto = texto(root, "data", "created_at", "criado_em");
        if (dataTexto != null) {
            try {
                data = Instant.parse(dataTexto);
            } catch (Exception ignored) {
                // formato de data inesperado: mantem Instant.now() em vez de falhar o callback inteiro
            }
        }

        String hashDedup = Hashing.sha256Hex(numeroCnj + "|" + rawBody);
        service.registrarMovimentacao(numeroCnj, data, evento != null ? evento : "DESCONHECIDO", descricao, hashDedup);
    }

    private static String texto(JsonNode node, String... chaves) {
        for (String chave : chaves) {
            JsonNode v = node.get(chave);
            if (v != null && !v.isNull()) {
                return v.asText();
            }
        }
        return null;
    }

    /** So o tamanho do payload vai pro log de auditoria — nunca o conteudo bruto (pode ter dado de terceiro). */
    private static String resumoParaLog(String rawBody) {
        return "payload de " + rawBody.length() + " bytes";
    }
}
