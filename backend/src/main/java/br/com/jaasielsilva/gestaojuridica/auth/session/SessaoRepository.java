package br.com.jaasielsilva.gestaojuridica.auth.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface SessaoRepository extends JpaRepository<Sessao, UUID> {

    @Query("select count(s) > 0 from Sessao s where s.id = :id and s.revogadaEm is null and s.expiraEm > :agora")
    boolean estaAtiva(@Param("id") UUID id, @Param("agora") Instant agora);

    @Modifying
    @Query("update Sessao s set s.revogadaEm = :agora where s.usuarioId = :usuarioId and s.revogadaEm is null")
    int revogarTodasDoUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    @Modifying
    @Query("update Sessao s set s.revogadaEm = :agora "
            + "where s.usuarioId = :usuarioId and s.id <> :mantida and s.revogadaEm is null")
    int revogarOutrasDoUsuario(@Param("usuarioId") Long usuarioId, @Param("mantida") UUID mantida,
                               @Param("agora") Instant agora);

    @Modifying
    @Query("delete from Sessao s where s.expiraEm < :limite")
    int apagarExpiradasAntesDe(@Param("limite") Instant limite);
}
