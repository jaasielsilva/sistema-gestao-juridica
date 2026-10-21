package br.com.jaasielsilva.gestaojuridica.auth.mfa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {

    /** Consome o codigo de forma atomica (retorna 1 se estava disponivel). */
    @Modifying
    @Query("update MfaRecoveryCode c set c.usadoEm = :agora "
            + "where c.usuarioId = :usuarioId and c.codigoHash = :hash and c.usadoEm is null")
    int consumir(@Param("usuarioId") Long usuarioId, @Param("hash") String hash, @Param("agora") Instant agora);

    @Modifying
    @Query("delete from MfaRecoveryCode c where c.usuarioId = :usuarioId")
    int apagarDoUsuario(@Param("usuarioId") Long usuarioId);
}
