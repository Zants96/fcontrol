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
    if (mes !== null) url += `&mes=${mes}`;
    if (tipo !== null) url += `&tipo=${tipo}`;
    
    const res = await fetch(url);
    if (!res.ok) throw new Error('Erro ao buscar insights');
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
};
