# Checklist de lancamento

## Seguranca
- [ ] `JWT_SECRET` aleatorio (>= 48 chars), diferente por ambiente, guardado em cofre/secret manager
- [ ] `DB_PASSWORD` forte; aplicacao usa usuario com **permissoes minimas** (nao `sa`)
- [ ] Usuario de migracao separado do usuario de runtime
- [ ] Conexao com o banco com certificado valido (remover `trustServerCertificate=true`)
- [ ] `SWAGGER_ENABLED=false` em producao (ou protegido)
- [ ] `CORS_ALLOWED_ORIGINS` apenas com os dominios reais
- [ ] HTTPS obrigatorio na frente da API (proxy/load balancer)
- [ ] Rate limit / protecao contra forca bruta no login
- [ ] Dependencias sem vulnerabilidades conhecidas (`mvn versions:display-dependency-updates`, Dependabot)
- [ ] Nenhum segredo no repositorio (`git log -p | grep -i secret`)

## Dados
- [ ] Backup automatico do SQL Server e restauracao **testada**
- [ ] Scripts de producao revisados e aplicados com `apply.sh`; `schema_version` conferida
- [ ] Politica de retencao e LGPD (base legal, exclusao/anonimizacao sob pedido)

## Operacao
- [ ] Health check (`/actuator/health`) monitorado
- [ ] Logs centralizados, sem senha/token; busca por `X-Correlation-Id`
- [ ] Alertas de erro 5xx e latencia
- [ ] Plano de rollback da versao e do banco
- [ ] CI verde na branch principal

## Produto
- [ ] Fluxos do PRD testados ponta a ponta
- [ ] Termos de uso e politica de privacidade publicados
- [ ] Dominio, e-mail transacional e suporte definidos
