-- Controle de concorrência otimista na OP (@Version): se duas pessoas concluírem a mesma ordem ao mesmo
-- tempo, a segunda recebe 409 em vez de sobrescrever a quantidade produzida.
-- SQL compatível com PostgreSQL e H2.
ALTER TABLE ordens_producao ADD COLUMN versao BIGINT DEFAULT 0 NOT NULL;
