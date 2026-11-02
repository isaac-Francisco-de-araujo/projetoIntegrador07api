-- Paginação temporal e vínculos usados por EXISTS (sem duplicar faturas).
CREATE INDEX ix_viagens_data_id ON viagens(data,id);
CREATE INDEX ix_clientes_nome_id ON clientes(nome,id);
CREATE INDEX ix_pacotes_data_id ON pacotes(criado_em,id);
CREATE INDEX ix_pacote_documentos_documento ON pacote_documentos(documento_id,pacote_id);
CREATE INDEX ix_documentos_arquivo_estado ON documentos(arquivado_em,id);
