-- 013_read_committed_snapshot.sql
-- READ_COMMITTED_SNAPSHOT: leituras (listagens longas) passam a usar versao de linha em vez de travas compartilhadas,
-- entao nao bloqueiam INSERT/UPDATE. No teste de carga, as escritas esperaram 3,3 s em media por trava (LCK_M_IX).
-- Atencao: ROLLBACK IMMEDIATE encerra as outras conexoes do banco; aplique antes de subir/reiniciar o backend
-- (o apply.sh ja roda antes dele no compose). Aumenta um pouco o uso do tempdb.
-- Rollback: ALTER DATABASE CURRENT SET READ_COMMITTED_SNAPSHOT OFF WITH ROLLBACK IMMEDIATE;

ALTER DATABASE CURRENT SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
