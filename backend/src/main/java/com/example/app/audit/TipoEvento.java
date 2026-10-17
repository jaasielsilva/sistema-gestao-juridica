package com.example.app.audit;

/** Eventos de seguranca registrados na trilha de auditoria. */
public enum TipoEvento {
    CADASTRO,
    LOGIN_SUCESSO,
    LOGIN_FALHA,
    LOGIN_MFA_PENDENTE,
    LOGOUT,
    LOGOUT_TODOS,
    REFRESH_REUSO,
    /** Reuso de refresh token dentro do grace period (abas concorrentes ou potencial roubo de cookie). */
    REFRESH_REUSO_GRACE,
    EMAIL_VERIFICADO,
    SENHA_RESET_SOLICITADO,
    SENHA_REDEFINIDA,
    SENHA_ALTERADA,
    MFA_HABILITADO,
    MFA_DESABILITADO,
    MFA_FALHA,
    MFA_CODIGO_RECUPERACAO_USADO
}
