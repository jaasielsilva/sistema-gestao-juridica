package br.com.jaasielsilva.gestaojuridica.processo;

import java.util.Map;

/** Cartoes do radar. porEtapa tem todas as etapas (zero quando vazia), na ordem do fluxo. */
public record RadarResumo(long total, long travados, long atencao, long fluindo, Map<EtapaProcesso, Long> porEtapa) {
}
