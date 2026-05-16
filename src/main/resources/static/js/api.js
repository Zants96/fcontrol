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
};
