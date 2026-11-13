ALTER TABLE documentos ADD COLUMN arquivado_em TIMESTAMP(6) WITH TIME ZONE;
CREATE INDEX idx_documentos_arquivados ON documentos(viagem_id, arquivado_em);
