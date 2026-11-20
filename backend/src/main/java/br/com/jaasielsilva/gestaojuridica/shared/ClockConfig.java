package br.com.jaasielsilva.gestaojuridica.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Relogio da aplicacao. Prazos processuais contam em dias do calendario brasileiro, entao "hoje" e a data em
 * America/Sao_Paulo (e nao a do servidor, que roda em UTC). Injetar o Clock tambem deixa o tempo controlavel nos testes.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock clock() {
        return Clock.system(FUSO);
    }
}
