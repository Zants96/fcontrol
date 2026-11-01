/**
 * inv-constants.js – Constantes e variáveis globais de Investimentos
 * Deve ser carregado ANTES de qualquer outro módulo inv-*.js
 */

const TIPO_ATIVO_LABELS = {
  ACAO: 'Ações', FII: 'FIIs', RENDA_FIXA: 'Renda Fixa',
  ETF: 'ETFs', TESOURO_DIRETO: 'Tesouro Direto', CRIPTO: 'Criptomoedas'
};

const TIPO_ATIVO_COLORS = {
  ACAO: '#60a5fa',          // Azul
  FII: '#fb923c',           // Laranja
  RENDA_FIXA: '#34d399',    // Verde esmeralda
  ETF: '#a78bfa',           // Roxo
  TESOURO_DIRETO: '#22d3ee', // Ciano
  CRIPTO: '#fbbf24'         // Amarelo
};

const TIPO_ATIVO_ORDER = ['ACAO', 'FII', 'RENDA_FIXA', 'ETF', 'TESOURO_DIRETO', 'CRIPTO'];

const CATEGORIA_TATICA_LABELS = {
  SEGURANCA: 'Segurança',
  SEGURANÇA: 'Segurança',
  RENDA: 'Renda',
  CRESCIMENTO: 'Crescimento',
  GLOBAL: 'Global',
  PREVIDENCIA: 'Previdência',
  PREVIDÊNCIA: 'Previdência'
};

const CATEGORIA_TATICA_COLORS = {
  SEGURANCA: '#34d399',    // Verde esmeralda — igual à Renda Fixa (ativos de segurança)
  SEGURANÇA: '#34d399',
  RENDA: '#22d3ee',        // Ciano — igual ao Tesouro Direto (geração de renda)
  CRESCIMENTO: '#fb923c',  // Laranja — ação/crescimento
  GLOBAL: '#a78bfa',       // Roxo — ETFs globais
  PREVIDENCIA: '#ec4899',  // Rosa — previdência
  PREVIDÊNCIA: '#ec4899'
};

const CATEGORIA_TATICA_ORDER = ['SEGURANCA', 'RENDA', 'CRESCIMENTO', 'GLOBAL', 'PREVIDENCIA'];

// Instâncias de Chart.js gerenciadas globalmente
let _invDistChart = null;
let _invEvolucaoChart = null;
let _invDividendosChart = null;
