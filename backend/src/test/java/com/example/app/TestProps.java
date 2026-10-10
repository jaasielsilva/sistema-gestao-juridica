package com.example.app;

import com.example.app.config.AppProperties;

/** Configuracao pronta para testes unitarios (sem subir o Spring). */
public final class TestProps {

    /** 32 bytes em base64 (chave AES-256 de teste; nao e segredo real). */
    public static final String MFA_KEY = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    private TestProps() {
    }

    public static AppProperties app(boolean registrationEnabled) {
        return new AppProperties(
                "http://localhost:3000",
                registrationEnabled,
                new AppProperties.Jwt("unit-test-secret-unit-test-secret-1234", "app", 5),
                new AppProperties.Refresh(7, false, 0),
                new AppProperties.Throttle(5, 30, 10, 15, 15),
                new AppProperties.Mfa("Teste", MFA_KEY),
                new AppProperties.Mail("teste@localhost", false));
    }
}
