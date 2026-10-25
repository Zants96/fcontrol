-- ──────────────────────────────────────────────────────────────────────────────
-- MyTwoCents Database Schema Migration - V5__abtc11_cripto.sql
-- ──────────────────────────────────────────────────────────────────────────────

-- Garante que o ETF ABTC11 (ETF de Bitcoin) seja classificado como CRIPTO e CRESCIMENTO
UPDATE ativo 
SET tipo_ativo = 'CRIPTO', 
    categoria_tatica = 'CRESCIMENTO' 
WHERE UPPER(ticker) = 'ABTC11';

-- Atualiza lançamentos financeiros vinculados para a subcategoria Criptomoedas
UPDATE lancamento 
SET subcategoria = 'Criptomoedas' 
WHERE id IN (
    SELECT il.lancamento_financeiro_id 
    FROM investimento_lancamento il 
    JOIN ativo a ON a.id = il.ativo_id 
    WHERE UPPER(a.ticker) = 'ABTC11' AND il.lancamento_financeiro_id IS NOT NULL
);
