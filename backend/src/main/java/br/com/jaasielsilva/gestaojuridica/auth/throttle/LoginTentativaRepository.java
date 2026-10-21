package br.com.jaasielsilva.gestaojuridica.auth.throttle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LoginTentativaRepository extends JpaRepository<LoginTentativa, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LoginTentativa t where t.chave = :chave")
    Optional<LoginTentativa> buscarParaAtualizar(@Param("chave") String chave);

    @Query("select t from LoginTentativa t where t.chave in :chaves and t.bloqueadoAte > :agora")
    List<LoginTentativa> bloqueadas(@Param("chaves") Collection<String> chaves, @Param("agora") Instant agora);

    @Modifying
    @Query("delete from LoginTentativa t where t.chave = :chave")
    int apagarPorChave(@Param("chave") String chave);

    @Modifying
    @Query("delete from LoginTentativa t where t.janelaInicio < :limite and (t.bloqueadoAte is null or t.bloqueadoAte < :agora)")
    int apagarAntigas(@Param("limite") Instant limite, @Param("agora") Instant agora);
}
