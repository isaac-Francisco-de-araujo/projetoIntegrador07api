CREATE TABLE configuracao_operacao (
    id INTEGER PRIMARY KEY CHECK(id=1),
    versao BIGINT NOT NULL,
    remetente VARCHAR(255),
    exportacao_automatica BOOLEAN NOT NULL,
    subpasta VARCHAR(60) NOT NULL,
    atualizada_em TIMESTAMP WITH TIME ZONE NOT NULL
);
INSERT INTO configuracao_operacao VALUES(1,0,NULL,FALSE,'',CURRENT_TIMESTAMP);
CREATE TABLE testes_configuracao (
    id VARCHAR(36) PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL,
    configuracao TEXT NOT NULL,
    situacao VARCHAR(40) NOT NULL,
    mensagem VARCHAR(500) NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE recebimentos (
    id VARCHAR(36) PRIMARY KEY,
    sha256 VARCHAR(64) NOT NULL UNIQUE,
    destinatario VARCHAR(255),
    remetente VARCHAR(255),
    simular BOOLEAN NOT NULL,
    configuracao TEXT NOT NULL,
    criado_em TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE notificacoes_recebimento (
    id VARCHAR(36) PRIMARY KEY,
    recebimento_id VARCHAR(36) NOT NULL REFERENCES recebimentos(id),
    evento VARCHAR(40) NOT NULL,
    chave_evento VARCHAR(160) NOT NULL,
    referencia VARCHAR(100) NOT NULL,
    remetente VARCHAR(255),
    destinatario VARCHAR(255),
    configuracao TEXT NOT NULL,
    assunto VARCHAR(255) NOT NULL,
    mensagem VARCHAR(2000) NOT NULL,
    modo VARCHAR(20) NOT NULL CHECK(modo='SIMULADO'),
    situacao VARCHAR(40) NOT NULL,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    concluida_em TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_notificacao_evento UNIQUE(recebimento_id,chave_evento)
);
CREATE TABLE exportacoes (
    id VARCHAR(36) PRIMARY KEY,
    pacote_id BIGINT NOT NULL REFERENCES pacotes(id),
    destino_chave VARCHAR(64) NOT NULL,
    subpasta VARCHAR(60) NOT NULL,
    configuracao TEXT NOT NULL,
    situacao VARCHAR(40) NOT NULL,
    mensagem VARCHAR(500) NOT NULL,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    atualizada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_exportacao_destino UNIQUE(pacote_id,destino_chave)
);
CREATE TABLE exportacao_arquivos (
    id VARCHAR(36) PRIMARY KEY,
    exportacao_id VARCHAR(36) NOT NULL REFERENCES exportacoes(id),
    origem VARCHAR(20) NOT NULL,
    origem_id BIGINT,
    caminho_fonte VARCHAR(1024),
    localizacao VARCHAR(255) NOT NULL,
    viagens TEXT NOT NULL,
    sha256 VARCHAR(64),
    tamanho BIGINT NOT NULL DEFAULT 0,
    manifesto TEXT,
    situacao VARCHAR(40) NOT NULL,
    etapa VARCHAR(40) NOT NULL,
    codigo VARCHAR(60),
    mensagem VARCHAR(500) NOT NULL,
    CONSTRAINT uk_exportacao_local UNIQUE(exportacao_id,localizacao)
);
CREATE INDEX ix_exportacoes_pacote ON exportacoes(pacote_id,criada_em);
