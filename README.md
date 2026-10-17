# Casca genérica de produto

Template para iniciar um produto com **Spring Boot 3.5, Java 21, SQL Server, Angular 22 e Angular Material**. Inclui autenticação com MFA, sessões seguras, isolamento por tenant, auditoria, Docker e CI.

## Início rápido

1. Copie `.env.example` para `.env`.
2. Gere segredos próprios e substitua `JWT_SECRET`, `MFA_ENCRYPTION_KEY`, `DB_PASSWORD` e `APP_DB_PASSWORD`. A API recusa o segredo JWT de exemplo.
3. Inicie os serviços:

   ```bash
   docker compose up --build
   ```

4. Acesse a aplicação em <http://localhost:3000>. A caixa de e-mail local fica em <http://localhost:8025>.

O cadastro está **desabilitado por padrão**. Para habilitá-lo, defina `REGISTRATION_ENABLED=true` no `.env` antes de iniciar. Cada novo cadastro cria um tenant; a primeira conta desse tenant recebe papel `ADMIN`. O servidor define o papel e o tenant. O e-mail é único globalmente, e a resposta para um e-mail já cadastrado é genérica.

As portas publicadas pelo Docker ficam limitadas a `127.0.0.1`. Esse ambiente é para desenvolvimento local.

## Criar um produto

```powershell
.\scripts\init-project.ps1 -Package br.com.minhaempresa.produto -ArtifactId produto
```

Depois, atualize a seção Produto em `CLAUDE.md` e preencha `docs/product/PRD.md`.

## Verificações de desenvolvimento

```bash
cd backend && mvn verify
cd frontend && npx ng test --watch=false
cd frontend && npx ng build
```

Os testes de integração do backend precisam de Docker e executam contra SQL Server.

## Documentação

- [Estado atual, controles e limitações](docs/ESTADO-ATUAL.md)
- [Checklist para lançamento](docs/product/launch-checklist.md)
- [Scripts e migrações do banco](database/README.md)
- [Decisões de arquitetura](docs/adr)

Antes de produção, configure HTTPS com certificado válido, segredos em cofre, SMTP, backup e monitoramento conforme o checklist.
