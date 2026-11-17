
-- RF008: relaciona ordens de producao com fichas tecnicas.
-- A tabela fichas_tecnicas ja existe nesta etapa das migrations.

ALTER TABLE ordens_producao
    ADD COLUMN ficha_tecnica_id BIGINT;

ALTER TABLE ordens_producao
    ADD CONSTRAINT fk_ordens_ficha_tecnica
    FOREIGN KEY (ficha_tecnica_id)
    REFERENCES fichas_tecnicas (id);

CREATE INDEX idx_ordens_ficha_tecnica
    ON ordens_producao (ficha_tecnica_id);
