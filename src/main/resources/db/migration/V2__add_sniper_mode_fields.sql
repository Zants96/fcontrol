-- ──────────────────────────────────────────────────────────────────────────────
-- MyTwoCents Database Schema Migration - V2__add_sniper_mode_fields.sql
-- ──────────────────────────────────────────────────────────────────────────────

ALTER TABLE ativo ADD COLUMN categoria_tatica VARCHAR(30) DEFAULT 'RENDA';
ALTER TABLE ativo ADD COLUMN is_ciclico BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ativo ADD COLUMN is_estrutural BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE ai_config ADD COLUMN emergency_box_target DECIMAL(15, 2) DEFAULT 20000.00;
ALTER TABLE ai_config ADD COLUMN monthly_income DECIMAL(15, 2) DEFAULT 5000.00;
