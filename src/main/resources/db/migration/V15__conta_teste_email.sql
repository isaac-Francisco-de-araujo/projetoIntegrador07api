-- Somente preferências públicas. Credenciais permanecem externas ao banco.
CREATE TABLE conta_teste_email (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    versao BIGINT NOT NULL DEFAULT 0,
    provedor VARCHAR(30) NOT NULL CHECK (provedor IN ('MICROSOFT_EXISTENTE','GMAIL')),
    remetente VARCHAR(255),
    autenticacao VARCHAR(20) NOT NULL CHECK (autenticacao IN ('APP_PASSWORD','XOAUTH2')),
    atualizada_em TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO conta_teste_email(id,provedor,autenticacao) VALUES(1,'MICROSOFT_EXISTENTE','XOAUTH2');
