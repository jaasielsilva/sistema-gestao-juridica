package br.com.jaasielsilva.gestaojuridica.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Habilita @Async (envio de e-mail fora da requisicao) e @Scheduled (limpeza periodica). */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncSchedulingConfig {
}
