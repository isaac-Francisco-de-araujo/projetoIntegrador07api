ALTER TABLE clientes ADD CONSTRAINT IF NOT EXISTS uk_clientes_cnpj UNIQUE (cnpj);
ALTER TABLE viagens ADD CONSTRAINT uk_viagens_numero UNIQUE (numero);
ALTER TABLE documentos ADD CONSTRAINT uk_documentos_chave UNIQUE (chave_acesso);
ALTER TABLE documentos ADD CONSTRAINT ck_documentos_identidade_xml CHECK (UPPER(tipo) <> 'XML' OR (chave_acesso IS NOT NULL AND sha256 IS NOT NULL AND REGEXP_LIKE(chave_acesso, '^[0-9]{44}$') AND REGEXP_LIKE(sha256, '^[0-9a-f]{64}$')));
