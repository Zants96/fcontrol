/**
 * api.js – Camada de comunicação com o backend Spring Boot
 */

const API_BASE = '/api';

const Api = {

  /**
   * Lista lançamentos por ano, com filtros opcionais de mês e categoria.
   */
  async getLancamentos({ ano, mes, categoria } = {}) {
    const params = new URLSearchParams();
    if (ano)       params.append('ano', ano);
    if (mes)       params.append('mes', mes);
    if (categoria) params.append('categoria', categoria);
    const res = await fetch(`${API_BASE}/lancamentos?${params}`);
    if (!res.ok) throw new Error('Erro ao buscar lançamentos');
    return res.json();
  },

  /**
   * Cria um novo lançamento.
   */
  async criarLancamento(dto) {
    const res = await fetch(`${API_BASE}/lancamentos`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dto),
    });
    if (!res.ok) throw new Error('Erro ao criar lançamento');
    return res.json();
  },

  /**
   * Atualiza um lançamento existente.
   */
  async atualizarLancamento(id, dto) {
    const res = await fetch(`${API_BASE}/lancamentos/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dto),
    });
    if (!res.ok) throw new Error('Erro ao atualizar lançamento');
    return res.json();
  },

  /**
   * Remove um lançamento.
   */
  async excluirLancamento(id, excluirProximos = false) {
    const res = await fetch(`${API_BASE}/lancamentos/${id}?excluirProximos=${excluirProximos}`, {
      method: 'DELETE',
    });
    if (!res.ok) throw new Error('Erro ao excluir lançamento');
  },

  /**
   * Busca dados agregados para o dashboard.
   */
  async getDashboard(ano) {
    const res = await fetch(`${API_BASE}/dashboard?ano=${ano}`);
    if (!res.ok) throw new Error('Erro ao buscar dashboard');
    return res.json();
  },

  // ─── IA ─────────────────────────────────────────────────────────────────

  /**
   * Envia uma mensagem ao chat da IA.
   */
  async sendAiChat(message, ano, historico = []) {
    const res = await fetch(`${API_BASE}/ai/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message, ano, historico }),
    });
    if (!res.ok) throw new Error('Erro ao consultar a IA');
    return res.json();
  },

  /**
   * Envia texto para o parser inteligente (fatura, boleto, comprovante).
   */
  async parseDocumento(texto, mes, ano) {
    const res = await fetch(`${API_BASE}/ai/parse`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ texto, mes, ano }),
    });
    if (!res.ok) throw new Error('Erro ao processar documento');
    return res.json();
  },

  /**
   * Busca insights automáticos da IA.
   */
  async getAiInsights(ano, mes = null, tipo = null) {
    let url = `${API_BASE}/ai/insights?ano=${ano}`;
    if (mes !== null && Number.isInteger(mes) && mes >= 1 && mes <= 12) url += `&mes=${mes}`;
    if (tipo !== null) url += `&tipo=${tipo}`;
    
    const res = await fetch(url);
    if (!res.ok) throw new Error('Erro ao buscar insights');
    return res.json();
  },

  /**
   * Busca insights automáticos da IA focados no portfólio de investimentos.
   */
  async getAiInsightsInvestimentos() {
    const res = await fetch(`${API_BASE}/ai/insights/investimentos`);
    if (!res.ok) throw new Error('Erro ao buscar insights de investimentos');
    return res.json();
  },

  /**
   * Verifica se a IA está configurada.
   */
  async getAiStatus() {
    const res = await fetch(`${API_BASE}/ai/status`);
    if (!res.ok) throw new Error('Erro ao verificar status da IA');
    return res.json();
  },

  /**
   * Salva a API Key da IA.
   */
  async saveAiConfig(apiKey, modelo, provider = 'gemini', apiUrl = null) {
    const res = await fetch(`${API_BASE}/ai/config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ apiKey, modelo, provider, apiUrl }),
    });
    if (!res.ok) throw new Error('Erro ao salvar configuração da IA');
    return res.json();
  },

  // ─── INVESTIMENTOS ──────────────────────────────────────────────────────

  async getInvestimentoDashboard() {
    const res = await fetch(`${API_BASE}/investimentos/dashboard`);
    if (!res.ok) throw new Error('Erro ao buscar dashboard de investimentos');
    return res.json();
  },

  async getAtivos(tipo) {
    let url = `${API_BASE}/investimentos/ativos`;
    if (tipo) url += `?tipo=${tipo}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Erro ao buscar ativos');
    return res.json();
  },

  async criarLancamentoInvestimento(dto) {
    const res = await fetch(`${API_BASE}/investimentos/lancamentos`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dto),
    });
    if (!res.ok) throw new Error('Erro ao criar lançamento de investimento');
    return res.json();
  },

  async getLancamentosInvestimento(ativoId) {
    let url = `${API_BASE}/investimentos/lancamentos`;
    if (ativoId) url += `?ativoId=${ativoId}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error('Erro ao buscar lançamentos');
    return res.json();
  },

  async getProventosHistorico() {
    const res = await fetch(`${API_BASE}/investimentos/proventos/historico`);
    if (!res.ok) throw new Error('Erro ao buscar histórico de proventos');
    return res.json();
  },

  async excluirLancamentoInvestimento(id) {
    const res = await fetch(`${API_BASE}/investimentos/lancamentos/${id}`, { method: 'DELETE' });
    if (!res.ok) throw new Error('Erro ao excluir lançamento');
  },

  async updateInvestimentoLancamento(id, dto) {
    const res = await fetch(`${API_BASE}/investimentos/lancamentos/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dto),
    });
    if (!res.ok) throw new Error('Erro ao atualizar lançamento');
    return res.json();
  },

  async atualizarPrecoAtivo(id, precoAtual) {
    const res = await fetch(`${API_BASE}/investimentos/ativos/${id}/preco`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ precoAtual }),
    });
    if (!res.ok) throw new Error('Erro ao atualizar preço');
    return res.json();
  },

  async atualizarAtivo(id, dto) {
    const res = await fetch(`${API_BASE}/investimentos/ativos/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dto),
    });
    if (!res.ok) throw new Error('Erro ao atualizar ativo');
    return res.json();
  },

  async atualizarCotacoes() {
    const res = await fetch(`${API_BASE}/investimentos/cotacoes/atualizar`, { method: 'POST' });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Erro ao atualizar cotações');
    return data;
  },

  async saveBrapiToken(token) {
    const res = await fetch(`${API_BASE}/investimentos/brapi/config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
    if (!res.ok) throw new Error('Erro ao salvar token BrAPI');
    return res.json();
  },

  async getBrapiStatus() {
    const res = await fetch(`${API_BASE}/investimentos/brapi/status`);
    if (!res.ok) throw new Error('Erro ao verificar status BrAPI');
    return res.json();
  },

  async saveCoingeckoKey(key) {
    const res = await fetch(`${API_BASE}/investimentos/coingecko/config`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key }),
    });
    if (!res.ok) throw new Error('Erro ao salvar chave CoinGecko');
    return res.json();
  },

  async getCoingeckoStatus() {
    const res = await fetch(`${API_BASE}/investimentos/coingecko/status`);
    if (!res.ok) throw new Error('Erro ao verificar status CoinGecko');
    return res.json();
  },

  async resetDatabase() {
    const res = await fetch(`${API_BASE}/backup/reset`, {
      method: 'POST',
    });
    if (!res.ok) {
      const msg = await res.text();
      throw new Error(msg || 'Erro ao zerar base de dados');
    }
    return res.text();
  },
  async checkUpdate() {
    const res = await fetch(`${API_BASE}/update/check`);
    if (!res.ok) throw new Error('Erro ao verificar atualizações');
    return res.json();
  },

  async startDownloadUpdate(url, fileName) {
    const params = new URLSearchParams();
    params.append('url', url);
    params.append('fileName', fileName);
    const res = await fetch(`${API_BASE}/update/download`, {
      method: 'POST',
      body: params
    });
    if (!res.ok) {
      const msg = await res.text();
      throw new Error(msg || 'Erro ao iniciar download');
    }
    return res.text();
  },

  async getDownloadProgress() {
    const res = await fetch(`${API_BASE}/update/progress`);
    if (!res.ok) throw new Error('Erro ao obter progresso do download');
    return res.json();
  },

  async applyUpdate() {
    const res = await fetch(`${API_BASE}/update/apply`, {
      method: 'POST'
    });
    if (!res.ok) {
      const msg = await res.text();
      throw new Error(msg || 'Erro ao aplicar atualização');
    }
    return res.text();
  },
};
