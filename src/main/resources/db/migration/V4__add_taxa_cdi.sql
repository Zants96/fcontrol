-- Adiciona coluna para taxa CDI no ai_config
ALTER TABLE ai_config ADD COLUMN IF NOT EXISTS taxa_cdi DECIMAL(5,2) DEFAULT 13.65;
