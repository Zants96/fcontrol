-- ──────────────────────────────────────────────────────────────────────────────
-- MyTwoCents Database Schema Migration - V1__init_schema.sql
-- ──────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS lancamento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL,
    categoria VARCHAR(20) NOT NULL,
    subcategoria VARCHAR(255) NOT NULL,
    valor DECIMAL(15, 2) NOT NULL,
    mes INT NOT NULL,
    ano INT NOT NULL,
    dia INT,
    parcela_actual INT,
    total_parcelas INT,
    grupo_id VARCHAR(255),
    criado_em TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ativo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticker VARCHAR(255) NOT NULL UNIQUE,
    nome VARCHAR(255),
    tipo_ativo VARCHAR(20) NOT NULL,
    quantidade DECIMAL(18, 8) NOT NULL DEFAULT 0,
    preco_medio DECIMAL(15, 2) NOT NULL DEFAULT 0,
    preco_atual DECIMAL(15, 2) NOT NULL DEFAULT 0,
    meta_percent DECIMAL(5, 2),
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    dividendos_total DECIMAL(15, 2) NOT NULL DEFAULT 0,
    logo_url VARCHAR(500),
    sector VARCHAR(100),
    long_name VARCHAR(200),
    data_vencimento DATE,
    indexador VARCHAR(50),
    taxa DECIMAL(15, 4),
    dividend_yield DECIMAL(15, 4),
    criado_em TIMESTAMP,
    atualizado_em TIMESTAMP
);

CREATE TABLE IF NOT EXISTS investimento_lancamento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ativo_id BIGINT NOT NULL,
    tipo_operacao VARCHAR(15) NOT NULL,
    data DATE NOT NULL,
    quantidade DECIMAL(18, 8) NOT NULL,
    preco_unitario DECIMAL(15, 2) NOT NULL,
    custos DECIMAL(15, 2) NOT NULL DEFAULT 0,
    valor_total DECIMAL(15, 2) NOT NULL,
    valor_liquido DECIMAL(15, 2),
    lancamento_financeiro_id BIGINT,
    data_vencimento DATE,
    indexador VARCHAR(50),
    tipo_provento VARCHAR(30),
    taxa DECIMAL(15, 4),
    criado_em TIMESTAMP,
    CONSTRAINT fk_investimento_ativo FOREIGN KEY (ativo_id) REFERENCES ativo(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS ai_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key VARCHAR(512) NOT NULL,
    provider VARCHAR(50) NOT NULL DEFAULT 'gemini',
    api_url VARCHAR(512),
    modelo VARCHAR(100) NOT NULL DEFAULT 'gemini-2.5-flash',
    brapi_token VARCHAR(512),
    coingecko_key VARCHAR(512),
    criado_em TIMESTAMP,
    atualizado_em TIMESTAMP
);
