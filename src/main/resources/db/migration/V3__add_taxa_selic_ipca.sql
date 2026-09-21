-- ──────────────────────────────────────────────────────────────────────────────
-- MyTwoCents Database Schema Migration - V3__add_taxa_selic_ipca.sql
-- ──────────────────────────────────────────────────────────────────────────────

ALTER TABLE ai_config ADD COLUMN taxa_selic DECIMAL(5, 2) DEFAULT 13.75;
ALTER TABLE ai_config ADD COLUMN taxa_ipca DECIMAL(5, 2) DEFAULT 4.22;
