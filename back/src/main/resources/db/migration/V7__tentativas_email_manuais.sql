CREATE TABLE conferencias_email (
    id VARCHAR(36) PRIMARY KEY,
    viagem_id BIGINT NOT NULL REFERENCES viagens(id),
    selecao TEXT NOT NULL,
    snapshot TEXT NOT NULL,
    anterior_id VARCHAR(36),
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    expira_em TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE tentativas_email (
    id VARCHAR(36) PRIMARY KEY,
    conferencia_id VARCHAR(36) NOT NULL UNIQUE REFERENCES conferencias_email(id),
    viagem_id BIGINT NOT NULL REFERENCES viagens(id),
    snapshot TEXT NOT NULL,
    anterior_id VARCHAR(36) REFERENCES tentativas_email(id),
    modo VARCHAR(20) NOT NULL CHECK (modo IN ('SIMULADO', 'REAL')),
    situacao VARCHAR(30) NOT NULL CHECK (situacao IN ('SIMULADO', 'FALHA', 'DESCONHECIDO', 'ACEITO_SMTP')),
    motivo VARCHAR(255) NOT NULL,
    criada_em TIMESTAMP WITH TIME ZONE NOT NULL,
    concluida_em TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_tentativas_email_viagem ON tentativas_email(viagem_id, criada_em);
ALTER TABLE conferencias_email ADD CONSTRAINT fk_conferencia_anterior FOREIGN KEY (anterior_id) REFERENCES tentativas_email(id);
