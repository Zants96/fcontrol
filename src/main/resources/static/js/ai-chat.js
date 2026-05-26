/**
 * ai-chat.js – Lógica do Assistente IA (Chat, Parser, Insights)
 */

// ─── Estado do Chat ──────────────────────────────────────────────────────────
const aiState = {
  messages: [],
  isLoading: false,
  insightsCache: null,
  insightsCacheTime: 0,
};

const INSIGHTS_CACHE_DURATION = 4 * 60 * 60 * 1000; // 4 horas

const SUGESTOES_RAPIDAS = [
  { label: '📊 Analise meus gastos', msg: 'Analise meus gastos deste ano e me dê recomendações.' },
  { label: '💰 Como economizar?', msg: 'O que posso fazer para economizar mais baseado nos meus dados?' },
  { label: '📈 Previsão do saldo', msg: 'Se eu manter esse ritmo, como será meu saldo no final do ano?' },
  { label: '📋 Colar fatura/boleto', msg: null }, // Abre input com placeholder diferente
];

// ─── Inicialização ───────────────────────────────────────────────────────────

function initAiChat() {
  const sendBtn = $('ai-send-btn');
  const input = $('ai-chat-input');
  
  if (!sendBtn || !input) return;

  sendBtn.addEventListener('click', () => sendAiMessage());
  input.addEventListener('keydown', (e) => {
    const isEnter = e.key === 'Enter' || e.keyCode === 13;
    const isCtrlOrMeta = e.ctrlKey || e.metaKey;
    
    if (isEnter && isCtrlOrMeta) {
      e.preventDefault();
      sendAiMessage();
    }
  });

  // Auto-resize do textarea
  input.addEventListener('input', () => {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 150) + 'px';
  });

  // Sugestões rápidas
  const sugContainer = $('ai-suggestions');
  if (sugContainer) {
    sugContainer.innerHTML = SUGESTOES_RAPIDAS.map((s, i) => `
      <button class="ai-suggestion-btn" id="ai-sug-${i}" data-idx="${i}">${s.label}</button>
    `).join('');
    
    sugContainer.querySelectorAll('.ai-suggestion-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const idx = parseInt(btn.dataset.idx);
        const sug = SUGESTOES_RAPIDAS[idx];
        if (sug.msg === null) {
          // "Colar fatura" — foca no input com placeholder especial
          input.placeholder = 'Cole aqui a fatura, boleto ou comprovante...';
          input.focus();
        } else {
          input.value = sug.msg;
          sendAiMessage();
        }
      });
    });
  }

  // Config da API key
  const saveKeyBtn = $('btn-save-ai-key');
  if (saveKeyBtn) {
    saveKeyBtn.addEventListener('click', saveAiApiKey);
  }

  const selectProvider = $('select-ai-provedor');
  if (selectProvider) {
    selectProvider.addEventListener('change', (e) => {
      triggerAiProviderChange(e.target.value, true);
    });
  }
}

// ─── Enviar Mensagem ─────────────────────────────────────────────────────────

async function sendAiMessage() {
  const input = $('ai-chat-input');
  const message = input.value.trim();
  if (!message || aiState.isLoading) return;

  // Reseta placeholder
  input.placeholder = 'Digite uma pergunta (Ctrl+Enter para enviar) ou cole uma fatura...';

  // Adiciona mensagem do usuário
  addChatMessage('user', message);
  input.value = '';
  input.style.height = 'auto';

  // Detecta se é um documento para parsear (heurística: linhas com valores R$, ou texto longo com múltiplas linhas)
  const isDocument = detectDocument(message);

  aiState.isLoading = true;
  showTypingIndicator();

  try {
    if (isDocument) {
      // Parser de documento
      const result = await Api.parseDocumento(message, state.mes, state.ano);
      hideTypingIndicator();
      
      if (result.error) {
        addChatMessage('ai', `❌ ${result.error}`);
      } else if (result.items && result.items.length > 0) {
        addChatMessage('ai', `📋 **Encontrei ${result.items.length} transações!**\n\n${result.resumo || ''}`);
        renderParsePreview(result.items);
      } else {
        addChatMessage('ai', '🤔 Não consegui identificar transações no texto. Tente colar novamente com mais detalhes.');
      }
    } else {
      // Chat normal com histórico de contexto
      const historico = aiState.messages
        .slice(0, -1) // desconsidera a última digitada que já está no parâmetro
        .map(msg => ({ role: msg.role, content: msg.content }));
      
      const result = await Api.sendAiChat(message, state.ano, historico);
      hideTypingIndicator();
      
      if (result.error) {
        addChatMessage('ai', `❌ ${result.error}`);
      } else {
        addChatMessage('ai', result.response);
      }
    }
  } catch (err) {
    hideTypingIndicator();
    addChatMessage('ai', `❌ Erro: ${err.message}`);
  } finally {
    aiState.isLoading = false;
  }
}

/**
 * Heurística para detectar se o texto é um documento/fatura.
 * Detecta: múltiplas linhas com valores monetários, padrões de fatura, etc.
 */
function detectDocument(text) {
  const lines = text.split('\n').filter(l => l.trim());
  if (lines.length < 2) return false;
  
  // Conta linhas com valores monetários (R$, números com vírgula ou ponto)
  const valorRegex = /\d+[.,]\d{2}/;
  const linhasComValor = lines.filter(l => valorRegex.test(l)).length;
  
  // Se mais de 50% das linhas têm valores e são pelo menos 3 linhas
  if (linhasComValor >= 3 && linhasComValor / lines.length > 0.3) return true;
  
  // Detecta palavras-chave de fatura/boleto
  const keywords = /fatura|boleto|comprovante|extrato|recibo|nota fiscal|nf-?e|cartão|nubank|inter|itaú|bradesco|santander|c6|pagamento/i;
  if (keywords.test(text) && linhasComValor >= 2) return true;
  
  return false;
}

// ─── Renderizar Mensagens ────────────────────────────────────────────────────

function addChatMessage(role, content) {
  aiState.messages.push({ role, content, time: new Date() });
  renderChatMessages();
}

function renderChatMessages() {
  const container = $('ai-chat-messages');
  if (!container) return;

  container.innerHTML = aiState.messages.map((msg, i) => {
    const isUser = msg.role === 'user';
    const time = msg.time.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    const formattedContent = isUser ? escHtml(msg.content) : formatMarkdown(msg.content);
    
    return `
      <div class="ai-msg ai-msg--${msg.role}" id="ai-msg-${i}">
        <div class="ai-msg-avatar">${isUser ? '👤' : '🤖'}</div>
        <div class="ai-msg-bubble">
          <div class="ai-msg-content">${formattedContent}</div>
          <div class="ai-msg-time">${time}</div>
        </div>
      </div>
    `;
  }).join('');

  // Scroll para baixo
  container.scrollTop = container.scrollHeight;
}

function showTypingIndicator() {
  const container = $('ai-chat-messages');
  if (!container) return;
  
  const indicator = document.createElement('div');
  indicator.id = 'ai-typing';
  indicator.className = 'ai-msg ai-msg--ai';
  indicator.innerHTML = `
    <div class="ai-msg-avatar">🤖</div>
    <div class="ai-msg-bubble">
      <div class="ai-typing-dots">
        <span></span><span></span><span></span>
      </div>
    </div>
  `;
  container.appendChild(indicator);
  container.scrollTop = container.scrollHeight;
}

function hideTypingIndicator() {
  const indicator = $('ai-typing');
  if (indicator) indicator.remove();
}

// ─── Preview do Parser ───────────────────────────────────────────────────────

function renderParsePreview(items) {
  const container = $('ai-chat-messages');
  if (!container) return;

  const categoriaLabels = {
    RECEITA: '💚 Receita',
    GASTO: '🔴 Gasto',
    GASTO_FIXO: '🟠 Gasto Fixo',
    ASSINATURA: '🟣 Assinatura',
  };

  const total = items.reduce((acc, item) => acc + parseFloat(item.valor || 0), 0);

  const previewHtml = `
    <div class="ai-msg ai-msg--ai" id="ai-parse-preview">
      <div class="ai-msg-avatar">📋</div>
      <div class="ai-msg-bubble ai-parse-bubble">
        <table class="ai-parse-table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Categoria</th>
              <th>Subcategoria</th>
              <th>Valor</th>
              <th>Dia</th>
            </tr>
          </thead>
          <tbody>
            ${items.map((item, i) => `
              <tr id="parse-row-${i}">
                <td>${escHtml(item.descricao)}</td>
                <td><span class="ai-cat-badge ai-cat-badge--${(item.categoria || '').toLowerCase()}">${categoriaLabels[item.categoria] || item.categoria}</span></td>
                <td>${escHtml(item.subcategoria)}</td>
                <td class="cell-valor">${fmtCurrency(parseFloat(item.valor))}</td>
                <td>${item.dia || '-'}</td>
              </tr>
            `).join('')}
          </tbody>
          <tfoot>
            <tr>
              <td colspan="3"><strong>Total</strong></td>
              <td class="cell-valor"><strong>${fmtCurrency(total)}</strong></td>
              <td></td>
            </tr>
          </tfoot>
        </table>
        <div class="ai-parse-actions">
          <button class="btn btn--primary" id="btn-parse-confirm" onclick="confirmParsedItems()">
            ✅ Cadastrar Todos (${items.length} itens)
          </button>
          <button class="btn btn--secondary" id="btn-parse-cancel" onclick="cancelParsedItems()">
            ✕ Descartar
          </button>
        </div>
      </div>
    </div>
  `;

  container.insertAdjacentHTML('beforeend', previewHtml);
  container.scrollTop = container.scrollHeight;

  // Salva items para uso posterior
  aiState.pendingItems = items;
}

async function confirmParsedItems() {
  if (!aiState.pendingItems || aiState.pendingItems.length === 0) return;

  const btn = $('btn-parse-confirm');
  if (btn) {
    btn.textContent = 'Cadastrando...';
    btn.disabled = true;
  }

  let success = 0;
  let errors = 0;

  for (const item of aiState.pendingItems) {
    try {
      await Api.criarLancamento({
        descricao: item.descricao,
        categoria: item.categoria,
        subcategoria: item.subcategoria,
        valor: parseFloat(item.valor),
        mes: state.mes,
        ano: state.ano,
        dia: item.dia,
        parcelas: 1,
      });
      success++;
    } catch (e) {
      errors++;
      console.error('Erro ao cadastrar item:', item, e);
    }
  }

  // Remove o preview
  const preview = $('ai-parse-preview');
  if (preview) preview.remove();

  // Feedback
  let msg = `✅ **${success} lançamento${success > 1 ? 's' : ''} cadastrado${success > 1 ? 's' : ''} com sucesso!**`;
  if (errors > 0) msg += `\n⚠️ ${errors} item(ns) falharam.`;
  addChatMessage('ai', msg);

  // Limpa
  aiState.pendingItems = null;
  _dashboardData = null; // Invalida cache do dashboard

  showToast(`${success} lançamentos cadastrados!`, 'success');
}

function cancelParsedItems() {
  const preview = $('ai-parse-preview');
  if (preview) preview.remove();
  aiState.pendingItems = null;
  addChatMessage('ai', '❌ Transações descartadas.');
}

// ─── Insights Automáticos ────────────────────────────────────────────────────

async function loadAiInsights(targetId = 'ai-insights-dashboard', mes = null, tipo = null) {
  const container = $(targetId);
  if (!container) return;

  // Se for insight focado por aba e mês, e não houver lançamentos ativos, não gera insight para poupar token!
  if (mes !== null && tipo !== null) {
    if (!state.lancamentos || state.lancamentos.length === 0) {
      container.innerHTML = `
        <div class="ai-insight-setup" style="border: 1px dashed rgba(255, 255, 255, 0.08); background: rgba(255, 255, 255, 0.02);">
          <div class="ai-insight-setup-icon" style="font-size: 1.5rem;">💡</div>
          <p style="margin: 0.25rem 0 0 0; color: var(--text-muted);">Nenhum lançamento nesta categoria para o mês selecionado. Adicione transações para receber insights da IA.</p>
        </div>
      `;
      return;
    }
  }

  // Verifica se IA está configurada
  try {
    const status = await Api.getAiStatus();
    if (!status.configured) {
      container.innerHTML = `
        <div class="ai-insight-setup">
          <div class="ai-insight-setup-icon">🤖</div>
          <p>Configure sua API Key nas Configurações para ativar os insights automáticos.</p>
          <button class="btn btn--primary" onclick="navigateTo('configuracoes')" style="margin-top:0.5rem">Configurar IA</button>
        </div>
      `;
      return;
    }
  } catch (e) {
    return;
  }

  // Gera a representação do estado atual dos lançamentos
  let currentStateString = "";
  
  if (targetId === 'ai-insights-investimentos') {
    // Para investimentos, usa o cache local de _invDashboardData
    if (typeof _invDashboardData !== 'undefined' && _invDashboardData) {
      currentStateString = (_invDashboardData.ativosPorTipo ? Object.values(_invDashboardData.ativosPorTipo).flat() : [])
        .map(a => `${a.id}-${a.quantidade}-${a.precoAtual}`)
        .join('|');
    }
  } else {
    // Para gastos
    currentStateString = (state.lancamentos || [])
      .map(l => `${l.id}-${l.valor}-${l.descricao}-${l.subcategoria}-${l.dia}`)
      .join('|');
  }

  // Verifica cache
  const now = Date.now();
  let cacheKey;
  if (targetId === 'ai-insights-investimentos') {
    cacheKey = `ai-insights-investimentos`;
  } else {
    cacheKey = mes !== null && tipo !== null ? `ai-insights-${state.ano}-${mes}-${tipo}` : `ai-insights-${state.ano}`;
  }
  const cached = localStorage.getItem(cacheKey);

  if (cached) {
    try {
      const cachedObj = JSON.parse(cached);
      
      if (mes !== null && tipo !== null) {
        // Para insights de categoria/mês: revalida puramente pela checksum dos lançamentos!
        if (cachedObj.stateString === currentStateString) {
          renderInsights(cachedObj.insights, targetId);
          return;
        }
      } else {
        // Para insights gerais do Dashboard principal: revalida por expiração de tempo (4h)
        const cachedTime = parseInt(localStorage.getItem(cacheKey + '-time') || '0');
        if ((now - cachedTime) < INSIGHTS_CACHE_DURATION) {
          renderInsights(cachedObj.insights || cachedObj, targetId);
          return;
        }
      }
    } catch (e) {
      // Se falhar o parse, prossegue para gerar novamente
    }
  }

  // Se NÃO estiver no cache (ou for cache inválido), exibe o botão elegante para geração sob demanda
  // Isso evita qualquer requisição automática de rede desnecessária e poupa sua cota gratuita!
  container.innerHTML = `
    <div class="ai-insight-setup" style="border: 1px dashed rgba(255, 255, 255, 0.08); background: rgba(255, 255, 255, 0.015); padding: 1.5rem; text-align: center; border-radius: 8px;">
      <div class="ai-insight-setup-icon" style="font-size: 1.75rem; margin-bottom: 0.5rem;">✨</div>
      <p style="margin: 0; color: var(--text-muted); font-size: 0.9rem;">Gostaria de analisar suas finanças deste período com Inteligência Artificial?</p>
      <button class="btn btn--primary" id="btn-gerar-insights-${targetId}" style="margin-top: 1rem; padding: 0.5rem 1.25rem; font-size: 0.85rem; display: inline-flex; align-items: center; gap: 0.5rem; border-radius: 6px;">
        <span>✨ Gerar Insights com IA</span>
      </button>
    </div>
  `;

  const btn = $(`btn-gerar-insights-${targetId}`);
  if (btn) {
    btn.addEventListener('click', () => {
      triggerGerarInsights(targetId, mes, tipo, currentStateString);
    });
  }
}

async function triggerGerarInsights(targetId, mes, tipo, currentStateString) {
  const container = $(targetId);
  if (!container) return;

  // Loading
  container.innerHTML = `
    <div class="ai-insights-loading" style="padding: 2rem 0;">
      <div class="ai-typing-dots"><span></span><span></span><span></span></div>
      <span style="color: var(--text-muted); margin-top: 0.5rem; font-size: 0.9rem;">Analisando suas transações com IA...</span>
    </div>
  `;

  try {
    let result;
    if (targetId === 'ai-insights-investimentos') {
      result = await Api.getAiInsightsInvestimentos();
    } else {
      result = await Api.getAiInsights(state.ano, mes, tipo);
    }
    
    if (result.error) {
      // Se estourar a cota de rate limit ou der erro 429
      if (result.error.includes("429") || result.error.toLowerCase().includes("quota") || result.error.toLowerCase().includes("rate limit") || result.error.toLowerCase().includes("exceeded")) {
        container.innerHTML = `
          <div class="ai-insight-error" style="background: rgba(239, 68, 68, 0.04); border: 1px dashed rgba(239, 68, 68, 0.2); color: #ef4444; padding: 1.25rem; border-radius: 8px; font-size: 0.9rem; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 0.75rem;">
            <span>⚠️ O limite gratuito de requisições da IA (20/min) foi atingido. Por favor, aguarde 30 segundos e tente gerar novamente.</span>
            <button class="btn btn--secondary" id="btn-retry-insights-${targetId}" style="padding: 0.4rem 1rem; font-size: 0.8rem; border-radius: 4px;">Tentar Novamente</button>
          </div>
        `;
        const retryBtn = $(`btn-retry-insights-${targetId}`);
        if (retryBtn) {
          retryBtn.addEventListener('click', () => {
            loadAiInsights(targetId, mes, tipo);
          });
        }
        return;
      }
      container.innerHTML = `<div class="ai-insight-error">⚠️ ${result.error}</div>`;
      return;
    }

    // Salva no cache com a checksum dos lançamentos no momento da geração
    const now = Date.now();
    let cacheKey;
    if (targetId === 'ai-insights-investimentos') {
      cacheKey = `ai-insights-investimentos`;
    } else {
      cacheKey = mes !== null && tipo !== null ? `ai-insights-${state.ano}-${mes}-${tipo}` : `ai-insights-${state.ano}`;
    }
    
    const cacheData = {
      insights: result.insights,
      stateString: currentStateString
    };
    localStorage.setItem(cacheKey, JSON.stringify(cacheData));
    localStorage.setItem(cacheKey + '-time', String(now));

    renderInsights(result.insights, targetId);
  } catch (err) {
    container.innerHTML = `
      <div class="ai-insight-error" style="background: rgba(239, 68, 68, 0.04); border: 1px dashed rgba(239, 68, 68, 0.2); color: #ef4444; padding: 1.25rem; border-radius: 8px; font-size: 0.9rem; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 0.75rem;">
        <span>⚠️ Limite gratuito de requisições temporariamente excedido. Aguarde 30 segundos e clique em Tentar Novamente.</span>
        <button class="btn btn--secondary" id="btn-retry-err-${targetId}" style="padding: 0.4rem 1rem; font-size: 0.8rem; border-radius: 4px;">Tentar Novamente</button>
      </div>
    `;
    const retryBtn = $(`btn-retry-err-${targetId}`);
    if (retryBtn) {
      retryBtn.addEventListener('click', () => {
        loadAiInsights(targetId, mes, tipo);
      });
    }
  }
}

async function loadAiInsightsTabela() {
  const container = $('ai-insights-tabela');
  if (!container) return;

  const titleEl = $('title-ai-insights-tabela');
  const divider = titleEl ? titleEl.closest('.section-divider') : null;

  if (state.categoria === 'RECEITA') {
    if (divider) divider.style.display = 'none';
    container.style.display = 'none';
    return;
  } else {
    if (divider) divider.style.display = 'flex';
    container.style.display = 'block';
  }

  if (titleEl) {
    const labelTipo = CATEGORIA_LABEL[state.categoria] || 'Gastos';
    const mesesNomes = ["Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
                        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"];
    const mesNome = mesesNomes[state.mes - 1] || state.mes;
    titleEl.textContent = `🤖 Insights de ${labelTipo} — ${mesNome}`;
  }

  const tipoParam = state.categoria.toLowerCase();
  await loadAiInsights('ai-insights-tabela', state.mes, tipoParam);
}

function renderInsights(insights, targetId) {
  const container = $(targetId);
  if (!container || !insights || insights.length === 0) {
    if (container) container.innerHTML = '';
    return;
  }

  const tipoClasses = {
    ALERTA: 'ai-insight--alerta',
    TENDENCIA: 'ai-insight--tendencia',
    DICA: 'ai-insight--dica',
    META: 'ai-insight--meta',
    POSITIVO: 'ai-insight--positivo',
  };

  container.innerHTML = `
    <div class="ai-insights-grid">
      ${insights.map((insight, i) => `
        <div class="ai-insight-card ${tipoClasses[insight.tipo] || ''}" style="animation-delay: ${i * 0.1}s" id="insight-${i}">
          <div class="ai-insight-icon">${insight.icone || '💡'}</div>
          <div class="ai-insight-content">
            <div class="ai-insight-tipo">${insight.tipo}</div>
            <div class="ai-insight-msg">${escHtml(insight.mensagem)}</div>
          </div>
        </div>
      `).join('')}
    </div>
  `;
}

// ─── API Key Config ──────────────────────────────────────────────────────────

async function saveAiApiKey() {
  const inputKey = $('input-ai-key');
  const inputModel = $('input-ai-modelo');
  const selectProvider = $('select-ai-provedor');
  const inputUrl = $('input-ai-url');
  const btn = $('btn-save-ai-key');
  if (!inputKey || !btn) return;

  const key = inputKey.value.trim();
  const model = inputModel ? inputModel.value.trim() : 'gemini-2.5-flash';
  const provider = selectProvider ? selectProvider.value : 'gemini';
  const apiUrl = (inputUrl && provider !== 'gemini') ? inputUrl.value.trim() : null;
  
  if (!key) {
    showToast('Digite a API Key.', 'error');
    return;
  }

  btn.textContent = 'Salvando...';
  btn.disabled = true;

  try {
    await Api.saveAiConfig(key, model, provider, apiUrl);
    showToast('Configuração salva com sucesso! Os insights serão gerados automaticamente.', 'success');
    // Limpa cache de insights para forçar regeneração
    localStorage.removeItem(`ai-insights-${state.ano}`);
    localStorage.removeItem(`ai-insights-${state.ano}-time`);
    // Atualiza status visual
    updateAiKeyStatus(true, model);
  } catch (err) {
    showToast('Erro ao salvar: ' + err.message, 'error');
  } finally {
    btn.textContent = 'Salvar';
    btn.disabled = false;
  }
}

async function checkAiKeyStatus() {
  try {
    const status = await Api.getAiStatus();
    updateAiKeyStatus(status.configured, status.modelo);
    if (status.configured) {
      const selectProvider = $('select-ai-provedor');
      const inputModel = $('input-ai-modelo');
      const inputUrl = $('input-ai-url');
      
      if (selectProvider && status.provider) {
        selectProvider.value = status.provider;
        triggerAiProviderChange(status.provider, false); // atualiza interface visual
      }
      if (inputModel && status.modelo) {
        inputModel.value = status.modelo;
      }
      if (inputUrl && status.apiUrl) {
        inputUrl.value = status.apiUrl;
      }
    }
  } catch (e) {
    updateAiKeyStatus(false);
  }
}

function triggerAiProviderChange(provider, resetValues = true) {
  const urlContainer = $('ai-custom-url-container');
  const inputUrl = $('input-ai-url');
  const inputModel = $('input-ai-modelo');
  const hintText = $('ai-key-hint-text');

  if (provider === 'gemini') {
    if (urlContainer) urlContainer.style.display = 'none';
    if (inputModel && resetValues) {
      inputModel.value = 'gemini-2.5-flash';
      inputModel.placeholder = 'gemini-2.5-flash';
    }
    if (hintText) {
      hintText.innerHTML = 'Crie sua chave gratuitamente em <a href="https://aistudio.google.com/" target="_blank" style="color: var(--brand-glow);">Google AI Studio</a>. Gratuito, sem cartão.';
    }
  } else {
    if (urlContainer) urlContainer.style.display = 'block';
    
    let url = '';
    let model = '';
    let hint = '';

    if (provider === 'deepseek') {
      url = 'https://api.deepseek.com/v1';
      model = 'deepseek-chat';
      hint = 'Crie sua chave na <a href="https://platform.deepseek.com/" target="_blank" style="color: var(--brand-glow);">DeepSeek Platform</a>.';
    } else if (provider === 'groq') {
      url = 'https://api.groq.com/openai/v1';
      model = 'llama-3.3-70b-versatile';
      hint = 'Crie sua chave no <a href="https://console.groq.com/" target="_blank" style="color: var(--brand-glow);">Groq Console</a>.';
    } else if (provider === 'grok') {
      url = 'https://api.x.ai/v1';
      model = 'grok-2-latest';
      hint = 'Crie sua chave na <a href="https://console.x.ai/" target="_blank" style="color: var(--brand-glow);">xAI Developer Console</a>.';
    } else if (provider === 'openai') {
      url = 'https://api.openai.com/v1';
      model = 'gpt-4o-mini';
      hint = 'Crie sua chave na <a href="https://platform.openai.com/" target="_blank" style="color: var(--brand-glow);">OpenAI Platform</a>.';
    } else {
      url = '';
      model = '';
      hint = 'Configure a URL do provedor de sua preferência compatível com OpenAI.';
    }

    if (resetValues) {
      if (inputUrl) inputUrl.value = url;
      if (inputModel) {
        inputModel.value = model;
        inputModel.placeholder = model || 'ex: gpt-4o';
      }
    }
    if (hintText) {
      hintText.innerHTML = hint;
    }
  }
}

function updateAiKeyStatus(configured, modelo = '') {
  const statusEl = $('ai-key-status');
  if (!statusEl) return;
  
  if (configured) {
    const modelLabel = modelo ? ` (${modelo})` : '';
    statusEl.innerHTML = `<span class="ai-key-ok">✅ Configurada${modelLabel}</span>`;
  } else {
    statusEl.innerHTML = '<span class="ai-key-missing">⚠️ Não configurada</span>';
  }
}

// ─── Markdown Simples ────────────────────────────────────────────────────────

function formatMarkdown(text) {
  if (!text) return '';
  
  let html = escHtml(text);
  
  // Negrito: **text**
  html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  
  // Itálico: *text* (sem lookbehind — compatível com WebKit/JavaFX)
  html = html.replace(/(?:^|[^*])\*([^*]+)\*(?:[^*]|$)/gm, function(match, p1) {
    // Preserva os caracteres ao redor que não são *
    var prefix = match.charAt(0) === '*' ? '' : match.charAt(0);
    var suffix = match.charAt(match.length - 1) === '*' ? '' : match.charAt(match.length - 1);
    return prefix + '<em>' + p1 + '</em>' + suffix;
  });
  
  // Código inline: `text`
  html = html.replace(/`(.+?)`/g, '<code>$1</code>');
  
  // Listas com -
  html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
  html = html.replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul>$1</ul>');
  
  // Listas numeradas
  html = html.replace(/^\d+\. (.+)$/gm, '<li>$1</li>');
  
  // Quebras de linha
  html = html.replace(/\n/g, '<br>');
  
  // Limpa BRs dentro de listas
  html = html.replace(/<\/li><br>/g, '</li>');
  html = html.replace(/<br><li>/g, '<li>');
  html = html.replace(/<br><ul>/g, '<ul>');
  html = html.replace(/<\/ul><br>/g, '</ul>');
  
  return html;
}
