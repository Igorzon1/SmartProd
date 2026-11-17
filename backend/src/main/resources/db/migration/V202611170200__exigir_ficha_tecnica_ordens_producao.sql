
-- RF008: toda ordem de producao deve possuir ficha tecnica.
-- Requer que ordens existentes ja tenham ficha_tecnica_id preenchido.

ALTER TABLE ordens_producao
    ALTER COLUMN ficha_tecnica_id SET NOT NULL;
