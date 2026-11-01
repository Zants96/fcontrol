-- ──────────────────────────────────────────────────────────────────────────────
-- MyTwoCents Database Schema Migration - V6__patrimonio_historico.sql
-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS patrimonio_historico (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ano INT NOT NULL,
    mes INT NOT NULL,
    valor_investido DECIMAL(15, 2) NOT NULL,
    valor_patrimonio DECIMAL(15, 2) NOT NULL,
    valor_lucro DECIMAL(15, 2) NOT NULL,
    valor_dividendos DECIMAL(15, 2) NOT NULL DEFAULT 0,
    atualizado_em TIMESTAMP,
    CONSTRAINT uk_patrimonio_ano_mes UNIQUE (ano, mes)
);
