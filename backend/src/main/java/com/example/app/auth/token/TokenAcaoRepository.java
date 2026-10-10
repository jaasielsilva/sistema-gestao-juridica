package com.example.app.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface TokenAcaoRepository extends JpaRepository<TokenAcao, Long> {

    /** Consome o token de forma atomica: so um chamador consegue marcar como usado (retorna 1). */
    @Modifying
    @Query("update TokenAcao t set t.usadoEm = :agora "
            + "where t.tokenHash = :hash and t.tipo = :tipo and t.usadoEm is null and t.expiraEm > :agora")
    int consumir(@Param("hash") String hash, @Param("tipo") TipoToken tipo, @Param("agora") Instant agora);

    @Query("select t.usuarioId from TokenAcao t where t.tokenHash = :hash")
    Optional<Long> usuarioDoToken(@Param("hash") String hash);

    /** Ao emitir um token novo, os anteriores ainda nao usados do mesmo tipo deixam de valer. */
    @Modifying
    @Query("update TokenAcao t set t.usadoEm = :agora where t.usuarioId = :usuarioId and t.tipo = :tipo and t.usadoEm is null")
    int invalidarPendentes(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoToken tipo, @Param("agora") Instant agora);

    @Modifying
    @Query("delete from TokenAcao t where t.expiraEm < :limite")
    int apagarExpiradosAntesDe(@Param("limite") Instant limite);
}
