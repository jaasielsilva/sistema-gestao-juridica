-- 013_rcsi_banco_de_usuario.sql
-- READ_COMMITTED_SNAPSHOT: leituras (listagens longas) passam a usar versao de linha em vez de travas compartilhadas,
-- entao nao bloqueiam INSERT/UPDATE. No teste de carga, as escritas esperaram 3,3 s em media por trava (LCK_M_IX).
-- So roda em banco de usuario (DB_ID() > 4): os testes de integracao aplicam os scripts no "master" do container,
-- que e banco de sistema e nao aceita ALTER DATABASE CURRENT.
-- Atencao: ROLLBACK IMMEDIATE encerra as outras conexoes do banco; aplique antes de subir/reiniciar o backend
-- (o apply.sh ja roda antes dele no compose). Aumenta um pouco o uso do tempdb. Se ja estiver ligado, nao muda nada.
-- Rollback: ALTER DATABASE CURRENT SET READ_COMMITTED_SNAPSHOT OFF WITH ROLLBACK IMMEDIATE;

-- EXEC dinamico: o ALTER DATABASE CURRENT e resolvido ja na compilacao do lote e falharia no master mesmo com o IF falso.
IF DB_ID() > 4 EXEC (N'ALTER DATABASE CURRENT SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE');
