-- 012_cliente_indices_desempenho.sql
-- Desempenho da listagem/busca de clientes com centenas de milhares de registros (teste de carga k6, 200 mil clientes).
-- ix_cliente_busca: ordenado por nome (mesma ordem da listagem) e com e-mail, documento, ativo, tipo, uf e cidade
--   incluidos, entao a busca por trecho e os filtros nao precisam ir ao registro principal (antes: ~600 mil leituras por busca).
-- ix_cliente_tenant_status: contagens/resumos por situacao e tipo viram busca no indice.
-- ix_cliente_tenant_nome (tenant_id, nome) fica redundante com ix_cliente_busca e e removido.
-- Rollback: DROP INDEX ix_cliente_busca ON dbo.cliente; DROP INDEX ix_cliente_tenant_status ON dbo.cliente;
--           CREATE INDEX ix_cliente_tenant_nome ON dbo.cliente (tenant_id, nome);

DROP INDEX ix_cliente_tenant_nome ON dbo.cliente;

CREATE INDEX ix_cliente_busca ON dbo.cliente (tenant_id, nome)
    INCLUDE (email, documento, ativo, tipo, uf, cidade);

CREATE INDEX ix_cliente_tenant_status ON dbo.cliente (tenant_id, ativo, tipo)
    INCLUDE (uf);
