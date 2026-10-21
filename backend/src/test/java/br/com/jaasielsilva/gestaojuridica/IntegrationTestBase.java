package br.com.jaasielsilva.gestaojuridica;

import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * Base dos testes de integracao: um unico SQL Server real (Testcontainers) compartilhado por todas as classes,
 * com o schema criado UMA vez a partir dos scripts reais de /database/scripts (em ordem). Se uma entidade divergir
 * do SQL, o ddl-auto=validate derruba o contexto. Ignorado automaticamente quando nao ha Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final MSSQLServerContainer<?> DB =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();

    static {
        if (org.testcontainers.DockerClientFactory.instance().isDockerAvailable()) {
            DB.start();
            applyScripts();
        }
    }

    /** Aplica todos os database/scripts/NNN_*.sql, em ordem. Novos scripts entram sozinhos (sem lista manual). */
    private static void applyScripts() {
        File[] scripts = new File("../database/scripts").listFiles((dir, name) -> name.matches("\\d{3}_.*\\.sql"));
        if (scripts == null || scripts.length == 0) {
            throw new IllegalStateException("Scripts SQL nao encontrados em ../database/scripts");
        }
        Arrays.sort(scripts);
        try (Connection c = DriverManager.getConnection(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword())) {
            for (File script : scripts) {
                // 000 usa IF/BEGIN...END (so para o apply.sh/sqlcmd); o teste nao precisa da tabela de controle.
                if (script.getName().startsWith("000_")) {
                    continue;
                }
                ScriptUtils.executeSqlScript(c, new FileSystemResource(script));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao aplicar scripts SQL no banco de teste", e);
        }
    }

    @DynamicPropertySource
    static void dbProps(DynamicPropertyRegistry r) {
        if (DB.isRunning()) {
            r.add("spring.datasource.url", DB::getJdbcUrl);
            r.add("spring.datasource.username", DB::getUsername);
            r.add("spring.datasource.password", DB::getPassword);
        }
    }
}
