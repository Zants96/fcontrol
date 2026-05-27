/**
 * investments.js – Lógica da aba de Investimentos do MyTwoCents
 */

const TIPO_ATIVO_LABELS = {
  ACAO: 'Ações', FII: 'FIIs', RENDA_FIXA: 'Renda Fixa',
  ETF: 'ETFs', TESOURO_DIRETO: 'Tesouro Direto', CRIPTO: 'Criptomoedas'
};

const TIPO_ATIVO_COLORS = {
  ACAO: '#60a5fa', FII: '#fb923c', RENDA_FIXA: '#34d399',
  ETF: '#a78bfa', TESOURO_DIRETO: '#10b981', CRIPTO: '#fbbf24'
};

const TIPO_ATIVO_ORDER = ['ACAO', 'FII', 'RENDA_FIXA', 'ETF', 'TESOURO_DIRETO', 'CRIPTO'];

let _invDistChart = null;
let _invEvolucaoChart = null;
let _invDividendosChart = null;

// ─── LOAD ────────────────────────────────────────────────────────────────

async function loadInvestimentos() {
  try {
    initInvEditModalListeners();
    const data = await Api.getInvestimentoDashboard();
    renderInvCards(data);
    renderInvDistChart(data);
    renderInvEvolucaoChart(data);
    renderInvDividendosChart(data);
    renderInvAcordeoes(data);

    // Binds para Exportação
    const btnCsv = $('btn-inv-export-csv');
    const btnPdf = $('btn-inv-export-pdf');
    if (btnCsv) btnCsv.onclick = () => downloadInvestimentos('csv');
    if (btnPdf) btnPdf.onclick = () => downloadInvestimentos('pdf');

    // Carrega botão/insights da IA para a carteira
    if (typeof loadAiInsights === 'function') {
      loadAiInsights('ai-insights-investimentos');
    }
  } catch (err) {
    console.error('Erro ao carregar investimentos:', err);
    showToast('Erro ao carregar investimentos: ' + err.message, 'error');
  }
}

// ─── CARDS ───────────────────────────────────────────────────────────────

function renderInvCards(data) {
  const pt = parseFloat(data.patrimonioTotal || 0);
  const vi = parseFloat(data.valorInvestido || 0);
  const lt = parseFloat(data.lucroTotal || 0);
  const dt = parseFloat(data.dividendosTotal || 0);
  const vp = parseFloat(data.variacaoPercent || 0);

  $('inv-card-patrimonio').textContent = fmtCurrency(pt);
  $('inv-card-investido').textContent = fmtCurrency(vi);
  $('inv-card-lucro').textContent = fmtCurrency(lt);
  $('inv-card-dividendos').textContent = fmtCurrency(dt);

  const varEl = $('inv-card-variacao');
  if (varEl) {
    varEl.textContent = fmtPercent(vp);
    varEl.className = 'card-sub ' + (vp >= 0 ? 'inv-variation--up' : 'inv-variation--down');
  }
}

// ─── DONUT CHART ─────────────────────────────────────────────────────────

function renderInvDistChart(data) {
  const dist = data.distribuicaoPorTipo || {};
  const labels = [];
  const values = [];
  const colors = [];

  TIPO_ATIVO_ORDER.forEach(tipo => {
    if (dist[tipo] && parseFloat(dist[tipo]) > 0) {
      labels.push(TIPO_ATIVO_LABELS[tipo] || tipo);
      values.push(parseFloat(dist[tipo]));
      colors.push(TIPO_ATIVO_COLORS[tipo] || '#666');
    }
  });

  const ctx = $('chart-inv-dist');
  if (!ctx) return;

  if (_invDistChart) _invDistChart.destroy();

  let emptyMsg = $('inv-chart-empty');
  if (values.length === 0) {
    if (!emptyMsg) {
      emptyMsg = document.createElement('p');
      emptyMsg.id = 'inv-chart-empty';
      emptyMsg.style = 'text-align:center;color:var(--text-muted);padding:2rem;position:absolute;width:100%;top:50%;left:0;transform:translateY(-50%);margin:0;';
      emptyMsg.textContent = 'Nenhum ativo cadastrado.';
      ctx.parentElement.style.position = 'relative';
      ctx.parentElement.appendChild(emptyMsg);
    }
    ctx.style.display = 'none';
    return;
  } else {
    if (emptyMsg) emptyMsg.remove();
    ctx.style.display = 'block';
  }

  const total = values.reduce((a, b) => a + b, 0);

  const isLight = document.documentElement.getAttribute('data-theme') === 'light';
  _invDistChart = new Chart(ctx, {
    type: 'doughnut',
    data: { 
      labels, 
      datasets: [{ 
        data: values, 
        backgroundColor: colors.map(c => c + 'cc'), 
        borderColor: isLight ? '#ccd9ecff' : '#111827',
        borderWidth: 1, 
        hoverBorderWidth: 0 
      }] 
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      cutout: '65%',
      plugins: {
        legend: {
          position: 'right',
          labels: {
            color: '#94a3b8',
            font: { family: 'Inter', size: 12 },
            boxWidth: 10,
            padding: 8,
          },
        },
        tooltip: {
          callbacks: {
            label: (ctx) => {
              const pct = ((ctx.raw / total) * 100).toFixed(1);
              return ` ${ctx.label}: ${fmtCurrency(ctx.raw)} (${pct}%)`;
            }
          }
        }
      }
    }
  });
}

// ─── EVOLUÇÃO E DIVIDENDOS ───────────────────────────────────────────────

function renderInvEvolucaoChart(data) {
  const ctx = $('chart-inv-evolucao');
  if (!ctx) return;
  if (_invEvolucaoChart) _invEvolucaoChart.destroy();

  const labels = data.labels12m || [];
  const investido = data.evolucaoInvestido12m || [];
  const patrimonio = data.evolucaoPatrimonio12m || [];
  const lucros = patrimonio.map((p, i) => p - investido[i]);
  const lucrosColors = lucros.map(v => v >= 0 ? '#34d399' : '#f87171'); // Verde claro ou Vermelho

  if (labels.length === 0) return;

  _invEvolucaoChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        {
          label: 'Valor Aplicado',
          data: investido,
          backgroundColor: '#059669', // Verde escuro
          borderRadius: 4
        },
        {
          label: 'Variação (Lucro/Prejuízo)',
          data: lucros,
          backgroundColor: lucrosColors,
          borderRadius: 4
        }
      ]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      interaction: {
        mode: 'index',
        intersect: false,
      },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            title: (ctx) => 'Mês: ' + ctx[0].label,
            label: (context) => {
              return ` ${context.dataset.label}: ${fmtCurrency(context.raw)}`;
            },
            footer: (contextItems) => {
              let valInvestido = 0;
              let valLucro = 0;
              contextItems.forEach(ci => {
                if (ci.dataset.label === 'Valor Aplicado') valInvestido = ci.raw;
                if (ci.dataset.label === 'Variação (Lucro/Prejuízo)') valLucro = ci.raw;
              });
              const valPatrimonio = valInvestido + valLucro;
              return `\nPatrimônio Total: ${fmtCurrency(valPatrimonio)}`;
            }
          }
        }
      },
      scales: {
        x: { 
          stacked: true, 
          grid: { color: 'rgba(255,255,255,0.05)' },
          ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() }
        },
        y: { 
          stacked: true, 
          beginAtZero: true,
          grid: { color: 'rgba(255,255,255,0.05)' },
          ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() }
        }
      }
    }
  });

  _invEvolucaoChart.update();
}

function renderInvDividendosChart(data) {
  const ctx = $('chart-inv-dividendos');
  if (!ctx) return;
  if (_invDividendosChart) _invDividendosChart.destroy();

  const labels = data.labels12m || [];
  const dividendos = data.evolucaoDividendos12m || [];

  if (labels.length === 0) return;

  _invDividendosChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'Dividendos',
        data: dividendos,
        backgroundColor: '#a78bfa',
        borderRadius: 4
      }]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: (ctx) => ` ${fmtCurrency(ctx.raw)}`
          }
        }
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() } },
        y: { beginAtZero: true, grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() } }
      }
    }
  });
}

// ─── ACORDEÕES ───────────────────────────────────────────────────────────

function renderInvAcordeoes(data) {
  const container = $('inv-acordeoes');
  if (!container) return;
  container.innerHTML = '';

  const resumo = data.resumoPorTipo || {};
  const ativosPorTipo = data.ativosPorTipo || {};

  TIPO_ATIVO_ORDER.forEach(tipo => {
    const ativos = ativosPorTipo[tipo];
    if (!ativos || ativos.length === 0) return;

    const r = resumo[tipo] || {};
    const color = TIPO_ATIVO_COLORS[tipo];
    const variacao = parseFloat(r.variacao || 0);
    const varClass = variacao >= 0 ? 'inv-variation--up' : 'inv-variation--down';

    const labelSetorSegmento = (tipo === 'FII' || tipo === 'ETF') ? 'Segmento' : 'Setor';
    const temSetorSegmento = (tipo === 'ACOES' || tipo === 'FII' || tipo === 'ETF');
    const isRendaFixaTesouro = (tipo === 'RENDA_FIXA' || tipo === 'TESOURO_DIRETO');
    const isRendaFixa = (tipo === 'RENDA_FIXA');
    const isTesouroDireto = (tipo === 'TESOURO_DIRETO');

    const section = document.createElement('div');
    section.className = 'inv-accordion';
    section.innerHTML = `
      <div class="inv-accordion-header" onclick="this.parentElement.classList.toggle('open')" style="border-left: 3px solid ${color};">
        <div class="inv-accordion-title">
          <span class="inv-badge" style="background:${color}20;color:${color};">${TIPO_ATIVO_LABELS[tipo]}</span>
          <span class="inv-accordion-count">${r.quantidadeAtivos || ativos.length} ativo(s)</span>
        </div>
        <div class="inv-accordion-stats">
          <span class="inv-accordion-val">${fmtCurrency(parseFloat(r.valorTotal || 0))}</span>
          <span class="${varClass}">${fmtPercent(variacao)}</span>
          <span class="inv-accordion-pct">${parseFloat(r.percentCarteira || 0).toFixed(0)}% da carteira</span>
          <svg class="inv-accordion-arrow" xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="6 9 12 15 18 9"/></svg>
        </div>
      </div>
      <div class="inv-accordion-body">
        <table class="data-table inv-table">
          <thead>
            <tr>
              <th>Ticker</th>
              ${isRendaFixa ? '' : '<th>Qtd</th>'}
              ${(isRendaFixa || isTesouroDireto) ? '' : '<th>PM</th>'}
              ${isRendaFixa ? '' : '<th>Atual</th>'}
              <th>Var%</th><th>Saldo</th>
              ${temSetorSegmento ? `<th>${labelSetorSegmento}</th>` : ''}
              ${isRendaFixaTesouro ? `<th>Vencimento</th><th>Taxa</th><th>Rend. Mensal</th>` : ''}
              <th>% Cart.</th><th class="col-actions">Ações</th>
            </tr>
          </thead>
          <tbody>
            ${ativos.map(a => {
              const v = parseFloat(a.variacao || 0);
              const vc = v >= 0 ? 'inv-variation--up' : 'inv-variation--down';
              return `<tr>
                <td>
                  <div class="inv-ticker-container" data-tooltip="${escHtml(a.longName || a.nome || a.ticker)}">
                    ${a.logoUrl ? `<img src="${a.logoUrl}" class="inv-ticker-logo" alt="${escHtml(a.ticker)}" onerror="this.style.display='none'" />` : ''}
                    <strong>${escHtml(a.ticker)}</strong>
                  </div>
                </td>
                ${isRendaFixa ? '' : `<td>${formatQtd(a.quantidade)}</td>`}
                ${(isRendaFixa || isTesouroDireto) ? '' : `<td>${fmtCurrency(parseFloat(a.precoMedio || 0))}</td>`}
                ${isRendaFixa ? '' : `<td>${fmtCurrency(parseFloat(a.precoAtual || 0))}</td>`}
                <td class="${vc}">${fmtPercent(v)}</td>
                <td>${fmtCurrency(parseFloat(a.valorTotal || 0))}</td>
                ${temSetorSegmento ? `<td>${escHtml(a.sector || '-')}</td>` : ''}
                ${isRendaFixaTesouro ? `
                  <td>${a.dataVencimento ? fmtDate(a.dataVencimento) : '-'}</td>
                  <td>${formatarTaxaIndexador(a.taxa, a.indexador)}</td>
                  <td style="font-weight: 600; color: var(--brand-glow);">${parseFloat(a.rendimentoMensal || 0) > 0 ? fmtCurrency(parseFloat(a.rendimentoMensal)) + ' /mês' : '-'}</td>
                ` : ''}
                <td>${parseFloat(a.percentCarteira || 0).toFixed(2)}%</td>
                <td class="col-actions">
                  <div class="action-btns">
                    <button class="action-btn action-btn--edit" title="Ver Histórico" onclick="abrirHistoricoAtivo(${a.id}, '${escHtml(a.ticker)}')">
                      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/></svg>
                    </button>
                  </div>
                </td>
              </tr>`;
            }).join('')}
          </tbody>
        </table>
        <div style="padding: 0.75rem 1rem; border-top: 1px solid var(--border);">
          <button class="btn btn--primary" onclick="openInvModal('${tipo}')" style="font-size:0.8rem;padding:0.5rem 1rem;">+ Adicionar Lançamento</button>
        </div>
      </div>`;
    container.appendChild(section);
  });

  // Se nenhum ativo existe, mostra mensagem
  if (container.children.length === 0) {
    container.innerHTML = `
      <div class="empty-state" style="padding:3rem;text-align:center;">
        <div class="empty-icon"><svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/></svg></div>
        <p>Nenhum investimento cadastrado.<br>Clique em <strong>+ Adicionar Lançamento</strong> para começar.</p>
        <button class="btn btn--primary" onclick="openInvModal()" style="margin-top:1rem;">+ Adicionar Lançamento</button>
      </div>`;
  }
}

function applyDateMask(input) {
  if (!input) return;
  input.addEventListener('input', (e) => {
    let val = e.target.value.replace(/\D/g, '');
    if (val.length > 8) val = val.substring(0, 8);
    if (val.length > 4) {
      val = val.substring(0, 2) + '/' + val.substring(2, 4) + '/' + val.substring(4);
    } else if (val.length > 2) {
      val = val.substring(0, 2) + '/' + val.substring(2);
    }
    e.target.value = val;
  });
}

function convertDateToIso(dateStr) {
  if (!dateStr) return null;
  if (/^\d{4}-\d{2}-\d{2}$/.test(dateStr)) return dateStr;
  const parts = dateStr.split('/');
  if (parts.length === 3) {
    const d = parseInt(parts[0], 10);
    const m = parseInt(parts[1], 10);
    const y = parseInt(parts[2], 10);
    
    if (parts[2].length === 4 && m >= 1 && m <= 12) {
      const dateObj = new Date(y, m - 1, d);
      if (dateObj.getFullYear() === y && dateObj.getMonth() === (m - 1) && dateObj.getDate() === d) {
        const dayStr = parts[0].padStart(2, '0');
        const monthStr = parts[1].padStart(2, '0');
        return `${y}-${monthStr}-${dayStr}`;
      }
    }
  }
  return null;
}

function formatIsoToBrDate(isoStr) {
  if (!isoStr) return '';
  const parts = isoStr.split('-');
  if (parts.length === 3) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }
  return isoStr;
}

function applyPercentMask(input) {
  if (!input) return;
  input.addEventListener('focus', (e) => {
    let val = e.target.value.replace('%', '').trim();
    e.target.value = val;
  });
  input.addEventListener('blur', (e) => {
    let val = e.target.value.replace('%', '').trim();
    if (val !== '') {
      e.target.value = val + '%';
    }
  });
}

// ─── MODAL ───────────────────────────────────────────────────────────────

function initInvModal() {
  const form = $('inv-form');
  if (!form) return;

  applyDateMask($('inv-form-vencimento'));
  applyDateMask($('inv-form-data'));
  applyPercentMask($('inv-form-taxa'));

  form.addEventListener('submit', onInvFormSubmit);
  $('inv-modal-close')?.addEventListener('click', closeInvModal);
  $('inv-btn-cancel')?.addEventListener('click', closeInvModal);
  $('inv-modal-overlay')?.addEventListener('click', e => {
    if (e.target === $('inv-modal-overlay')) closeInvModal();
  });

  // Radio toggle
  document.querySelectorAll('input[name="inv-operacao"]').forEach(r => {
    r.addEventListener('change', () => {
      const isDividendo = r.value === 'DIVIDENDO';
      $('inv-group-quantidade').style.display = isDividendo ? 'none' : '';
      $('inv-label-preco').textContent = isDividendo ? 'Valor recebido (R$)' : 'Preço unitário (R$)';
    });
  });

  const formatCurrency = (e) => {
    let val = e.target.value.replace(/\D/g, ''); // só números
    if (val === '') return;
    val = (parseInt(val, 10) / 100).toFixed(2); 
    val = val.replace('.', ',');
    val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.'); 
    e.target.value = val;
    calcInvTotal();
  };

  // Calcular valor total em tempo real
  $('inv-form-preco')?.addEventListener('input', formatCurrency);
  $('inv-form-custos')?.addEventListener('input', formatCurrency);
  $('inv-form-quantidade')?.addEventListener('input', calcInvTotal);

  const updateRendaFixaFields = () => {
    const tipo = $('inv-form-tipo')?.value;
    const fields = $('inv-form-renda-fixa-fields');
    if (fields) {
      if (tipo === 'RENDA_FIXA' || tipo === 'TESOURO_DIRETO') {
        fields.classList.remove('hidden');
        $('inv-form-vencimento').required = true;
        $('inv-form-taxa').required = true;
      } else {
        fields.classList.add('hidden');
        $('inv-form-vencimento').required = false;
        $('inv-form-taxa').required = false;
      }
    }
  };
  $('inv-form-tipo')?.addEventListener('change', updateRendaFixaFields);
}

function openInvModal(tipoAtivo) {
  $('inv-form').reset();
  if (tipoAtivo) $('inv-form-tipo').value = tipoAtivo;

  // Trigger change event to set conditional fields visibility
  const event = new Event('change');
  $('inv-form-tipo')?.dispatchEvent(event);

  // Default: Compra
  const compraRadio = document.querySelector('input[name="inv-operacao"][value="COMPRA"]');
  if (compraRadio) compraRadio.checked = true;
  $('inv-group-quantidade').style.display = '';
  $('inv-label-preco').textContent = 'Preço unitário (R$)';

  // Data de hoje (formato brasileiro DD/MM/YYYY)
  const today = new Date();
  const todayD = String(today.getDate()).padStart(2, '0');
  const todayM = String(today.getMonth() + 1).padStart(2, '0');
  const todayY = today.getFullYear();
  $('inv-form-data').value = `${todayD}/${todayM}/${todayY}`;
  $('inv-form-total').textContent = 'R$ 0,00';

  $('inv-modal-overlay').classList.remove('hidden');
  $('main-content').setAttribute('aria-hidden', 'true');
  setTimeout(() => $('inv-form-ticker')?.focus(), 100);
}

function closeInvModal() {
  $('inv-modal-overlay').classList.add('hidden');
  $('main-content').removeAttribute('aria-hidden');
}

function parseInvInput(val) {
  if (!val) return 0;
  if (typeof val === 'string') {
    val = val.replace('%', '').trim();
  }
  if (val.includes(',')) {
    return parseFloat(val.replace(/\./g, '').replace(',', '.'));
  }
  return parseFloat(val);
}

function calcInvTotal() {
  const operacao = document.querySelector('input[name="inv-operacao"]:checked')?.value;
  const preco = parseInvInput($('inv-form-preco')?.value);
  const custos = parseInvInput($('inv-form-custos')?.value);

  let total;
  if (operacao === 'DIVIDENDO') {
    total = preco + custos;
  } else {
    const qtd = parseInvInput($('inv-form-quantidade')?.value);
    total = (qtd * preco) + custos;
  }

  $('inv-form-total').textContent = fmtCurrency(total);
}

async function onInvFormSubmit(e) {
  e.preventDefault();

  const operacao = document.querySelector('input[name="inv-operacao"]:checked')?.value;
  const ticker = $('inv-form-ticker')?.value?.trim().toUpperCase();
  const tipoAtivo = $('inv-form-tipo')?.value;
  const dataRaw = $('inv-form-data')?.value;
  const data = convertDateToIso(dataRaw);
  const quantidade = parseInvInput($('inv-form-quantidade')?.value);
  const preco = parseInvInput($('inv-form-preco')?.value);
  const custos = parseInvInput($('inv-form-custos')?.value);

  const dataVencimentoRaw = $('inv-form-vencimento')?.value || null;
  const dataVencimento = convertDateToIso(dataVencimentoRaw);
  const indexador = $('inv-form-indexador')?.value || null;
  const taxaRaw = $('inv-form-taxa')?.value || null;
  const taxa = taxaRaw ? parseInvInput(taxaRaw) : null;

  if (!ticker) { showToast('Informe o ticker do ativo.', 'error'); return; }
  if (!data || !/^\d{4}-\d{2}-\d{2}$/.test(data)) {
    showToast('Informe uma data de operação válida (DD/MM/YYYY).', 'error');
    return;
  }
  
  if (tipoAtivo === 'RENDA_FIXA' || tipoAtivo === 'TESOURO_DIRETO') {
    if (!dataVencimento || !/^\d{4}-\d{2}-\d{2}$/.test(dataVencimento)) {
      showToast('Informe uma data de vencimento válida (DD/MM/YYYY).', 'error');
      return;
    }
  }

  if (preco <= 0) { showToast('Informe um preço válido.', 'error'); return; }
  if (operacao !== 'DIVIDENDO' && quantidade <= 0) {
    showToast('Informe uma quantidade válida.', 'error'); return;
  }

  const dto = {
    ticker, tipoAtivo, tipoOperacao: operacao,
    data, quantidade: operacao === 'DIVIDENDO' ? 0 : quantidade,
    precoUnitario: preco, custos,
    dataVencimento, indexador, taxa
  };

  const btn = $('inv-btn-save');
  btn.textContent = 'Salvando...';
  btn.disabled = true;

  try {
    await Api.criarLancamentoInvestimento(dto);
    showToast('Lançamento registrado!', 'success');
    closeInvModal();
    loadInvestimentos();
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    btn.textContent = 'Adicionar Lançamento';
    btn.disabled = false;
  }
}

// ─── EDITAR PREÇO ────────────────────────────────────────────────────────

function closeInvPrecoModal() {
  $('inv-preco-modal-overlay')?.classList.add('hidden');
  $('main-content')?.removeAttribute('aria-hidden');
}

async function editarPrecoAtivo(id, ticker, precoAtual) {
  if (!$('inv-preco-modal-overlay')) {
    const modalHtml = `
      <div id="inv-preco-modal-overlay" class="modal-overlay hidden" style="z-index: 2000;">
        <div class="modal inv-modal">
          <div class="modal-header">
            <h2>Atualizar Preço</h2>
            <button class="modal-close" id="inv-preco-modal-close" aria-label="Fechar janela">✕</button>
          </div>
          <form id="inv-preco-form" class="modal-body">
            <input type="hidden" id="inv-preco-id" />
            <input type="hidden" id="inv-preco-ticker" />
            
            <p id="inv-preco-modal-desc" style="margin-bottom: 1.5rem; color: var(--text-muted);"></p>

            <div class="form-group">
              <label for="inv-preco-valor">Novo preço unitário (R$)</label>
              <input type="text" id="inv-preco-valor" class="form-input" required />
            </div>

            <div class="modal-footer" style="margin-top: 1.5rem;">
              <button type="button" class="btn btn--secondary" id="inv-preco-btn-cancel">Cancelar</button>
              <button type="submit" class="btn btn--primary">Salvar</button>
            </div>
          </form>
        </div>
      </div>
    `;
    document.body.insertAdjacentHTML('beforeend', modalHtml);

    const form = $('inv-preco-form');
    $('inv-preco-modal-close')?.addEventListener('click', closeInvPrecoModal);
    $('inv-preco-btn-cancel')?.addEventListener('click', closeInvPrecoModal);
    $('inv-preco-modal-overlay')?.addEventListener('click', e => {
      if (e.target === $('inv-preco-modal-overlay')) closeInvPrecoModal();
    });

    $('inv-preco-valor')?.addEventListener('input', (e) => {
      let val = e.target.value.replace(/\D/g, ''); 
      if (val === '') return;
      val = (parseInt(val, 10) / 100).toFixed(2); 
      val = val.replace('.', ',');
      val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.'); 
      e.target.value = val;
    });

    form.addEventListener('submit', async (e) => {
      e.preventDefault();
      const idStr = $('inv-preco-id').value;
      const tck = $('inv-preco-ticker').value;
      
      const novoPrecoStr = $('inv-preco-valor').value;
      const preco = parseInvInput(novoPrecoStr);
      
      if (isNaN(preco) || preco < 0) {
        showToast('Preço inválido.', 'error');
        return;
      }

      try {
        await Api.atualizarPrecoAtivo(idStr, preco);
        showToast(`Preço de ${tck} atualizado!`, 'success');
        closeInvPrecoModal();
        loadInvestimentos();
      } catch (err) {
        showToast(err.message, 'error');
      }
    });
  }

  // Preenche o modal
  $('inv-preco-id').value = id;
  $('inv-preco-ticker').value = ticker;
  $('inv-preco-modal-desc').innerHTML = `Preço atual de <strong>${ticker}</strong>: R$ ${parseFloat(precoAtual).toFixed(2).replace('.', ',')}`;
  
  $('inv-preco-valor').value = fmtCurrency(precoAtual).replace('R$', '').trim();
  
  // Exibe o modal
  $('inv-preco-modal-overlay').classList.remove('hidden');
  $('main-content').setAttribute('aria-hidden', 'true');
}

// ─── HISTÓRICO DE ATIVO ──────────────────────────────────────────────────

async function abrirHistoricoAtivo(ativoId, ticker) {
  const modal = $('inv-historico-overlay');
  if (!modal) return;
  
  $('inv-historico-title').textContent = `Histórico: ${ticker}`;
  $('inv-historico-tbody').innerHTML = '<tr><td colspan="7" style="text-align:center;">Carregando...</td></tr>';
  $('inv-historico-empty').classList.add('hidden');
  
  modal.classList.remove('hidden');
  $('main-content').setAttribute('aria-hidden', 'true');

  $('inv-historico-close').onclick = () => {
    modal.classList.add('hidden');
    $('main-content').removeAttribute('aria-hidden');
  };
  modal.onclick = (e) => {
    if (e.target === modal) $('inv-historico-close').click();
  };

  try {
    const lancamentos = await Api.getLancamentosInvestimento(ativoId);
    renderizarTabelaHistorico(lancamentos);
  } catch (err) {
    $('inv-historico-tbody').innerHTML = '';
    showToast(err.message, 'error');
  }
}

function renderizarTabelaHistorico(lancamentos) {
  const tbody = $('inv-historico-tbody');
  tbody.innerHTML = '';

  if (!lancamentos || lancamentos.length === 0) {
    $('inv-historico-empty').classList.remove('hidden');
    return;
  }

  $('inv-historico-empty').classList.add('hidden');
  window._currentLancamentos = lancamentos;

  const getOpBadge = (op) => {
    if (op === 'COMPRA') return `<span class="inv-badge" style="background:#34d39920;color:#34d399;">COMPRA</span>`;
    if (op === 'VENDA') return `<span class="inv-badge" style="background:#f8717120;color:#f87171;">VENDA</span>`;
    return `<span class="inv-badge" style="background:#a78bfa20;color:#a78bfa;">DIVIDENDO</span>`;
  };

  lancamentos.forEach(l => {
    const tr = document.createElement('tr');
    
    // Converte a data yyyy-mm-dd para dd/mm/yyyy
    let dataFormatada = l.data;
    if (l.data) {
      const parts = l.data.split('-');
      if (parts.length === 3) dataFormatada = `${parts[2]}/${parts[1]}/${parts[0]}`;
    }

    tr.innerHTML = `
      <td>${dataFormatada}</td>
      <td>${getOpBadge(l.tipoOperacao)}</td>
      <td>${l.tipoOperacao === 'DIVIDENDO' ? '-' : formatQtd(l.quantidade)}</td>
      <td>${fmtCurrency(parseFloat(l.precoUnitario || 0))}</td>
      <td>${fmtCurrency(parseFloat(l.custos || 0))}</td>
      <td><strong>${fmtCurrency(parseFloat(l.valorTotal || 0))}</strong></td>
      <td class="col-actions">
        <div class="action-btns" style="justify-content: flex-start; gap: 0.5rem;">
          <button class="action-btn action-btn--edit" title="Editar Lançamento" onclick="editarLancamentoInvestimento(${l.id})">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
          </button>
          <button class="action-btn action-btn--delete" title="Excluir" onclick="excluirLancamentoInvestimento(${l.id}, ${l.ativoId}, '${escHtml(l.ticker)}')">
            <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#f87171" stroke-width="2"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
          </button>
        </div>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

async function excluirLancamentoInvestimento(lancamentoId, ativoId, ticker) {
  if (!confirm(`Tem certeza que deseja excluir este lançamento de ${ticker}? O preço médio será recalculado.`)) return;
  
  try {
    await Api.excluirLancamentoInvestimento(lancamentoId);
    showToast('Lançamento excluído com sucesso!', 'success');
    
    // Atualiza o histórico e o dashboard por trás
    const lancamentos = await Api.getLancamentosInvestimento(ativoId);
    renderizarTabelaHistorico(lancamentos);
    loadInvestimentos();
  } catch (err) {
    showToast(err.message, 'error');
  }
}

async function editarLancamentoInvestimento(id) {
  const l = window._currentLancamentos?.find(x => x.id === id);
  if (!l) return;

  // Preenche o modal
  $('inv-edit-id').value = l.id;
  $('inv-edit-ativo-id').value = l.ativoId;
  $('inv-edit-op').value = l.tipoOperacao;
  
  const formQtd = $('inv-edit-qtd');
  const formPreco = $('inv-edit-preco');
  const formCustos = $('inv-edit-custos');
  const groupQtd = $('inv-edit-group-qtd');
  const labelPreco = $('inv-edit-label-preco');
  
  if (l.tipoOperacao === 'DIVIDENDO') {
    groupQtd.style.display = 'none';
    formQtd.value = '';
    labelPreco.textContent = 'Valor do dividendo (R$)';
  } else {
    groupQtd.style.display = '';
    formQtd.value = formatQtd(l.quantidade);
    labelPreco.textContent = 'Preço unitário (R$)';
  }
  
  formPreco.value = fmtCurrency(l.precoUnitario || 0).replace('R$', '').trim();
  formCustos.value = fmtCurrency(l.custos || 0).replace('R$', '').trim();

  // Campos condicionais de Renda Fixa/Tesouro
  const groupRendaFixa = $('inv-edit-group-renda-fixa');
  if (groupRendaFixa) {
    const isRF = (l.tipoAtivo === 'RENDA_FIXA' || l.tipoAtivo === 'TESOURO_DIRETO');
    if (isRF) {
      groupRendaFixa.classList.remove('hidden');
      $('inv-edit-vencimento').value = formatIsoToBrDate(l.dataVencimento);
      $('inv-edit-indexador').value = l.indexador || 'CDI';
      $('inv-edit-taxa').value = l.taxa !== null ? l.taxa.toString().replace('.', ',') + '%' : '';
      $('inv-edit-vencimento').required = true;
      $('inv-edit-taxa').required = true;
    } else {
      groupRendaFixa.classList.add('hidden');
      $('inv-edit-vencimento').value = '';
      $('inv-edit-indexador').value = 'CDI';
      $('inv-edit-taxa').value = '';
      $('inv-edit-vencimento').required = false;
      $('inv-edit-taxa').required = false;
    }
  }
  
  // Exibe o modal
  $('inv-edit-modal-overlay').classList.remove('hidden');
  $('main-content').setAttribute('aria-hidden', 'true');
}

function closeInvEditModal() {
  $('inv-edit-modal-overlay').classList.add('hidden');
  $('main-content').removeAttribute('aria-hidden');
}

// Binds do modal de edição (chamados uma vez na inicialização)
function initInvEditModalListeners() {
  if (!$('inv-edit-modal-overlay')) {
    const modalHtml = `
      <div id="inv-edit-modal-overlay" class="modal-overlay hidden" style="z-index: 2000;">
        <div class="modal inv-modal">
          <div class="modal-header">
            <h2>Editar Lançamento</h2>
            <button class="modal-close" id="inv-edit-modal-close" aria-label="Fechar janela">✕</button>
          </div>
          <form id="inv-edit-form" class="modal-body">
            <input type="hidden" id="inv-edit-id" />
            <input type="hidden" id="inv-edit-ativo-id" />
            <input type="hidden" id="inv-edit-op" />
            
            <div class="form-group" id="inv-edit-group-qtd">
              <label for="inv-edit-qtd">Quantidade</label>
              <input type="text" id="inv-edit-qtd" class="form-input" />
            </div>
            <div class="form-group">
              <label for="inv-edit-preco" id="inv-edit-label-preco">Preço unitário (R$)</label>
              <input type="text" id="inv-edit-preco" class="form-input" required />
            </div>
            <div class="form-group">
              <label for="inv-edit-custos">Custos / Taxas (R$)</label>
              <input type="text" id="inv-edit-custos" class="form-input" />
            </div>

            <!-- Campos adicionais de Renda Fixa/Tesouro -->
            <div id="inv-edit-group-renda-fixa" class="hidden">
              <div class="form-group">
                <label for="inv-edit-vencimento">Data de Vencimento</label>
                <input type="text" id="inv-edit-vencimento" class="form-input" placeholder="DD/MM/YYYY" maxlength="10" />
              </div>
              <div class="form-group">
                <label for="inv-edit-indexador">Indexador</label>
                <select id="inv-edit-indexador" class="form-input">
                  <option value="CDI">CDI</option>
                  <option value="SELIC">SELIC</option>
                  <option value="IPCA">IPCA</option>
                  <option value="PRE">PRÉ</option>
                </select>
              </div>
              <div class="form-group">
                <label for="inv-edit-taxa">Taxa (%)</label>
                <input type="text" id="inv-edit-taxa" class="form-input" placeholder="Ex: 100 ou 6,2" />
              </div>
            </div>

            <div class="modal-footer" style="margin-top: 1.5rem;">
              <button type="button" class="btn btn--secondary" id="inv-edit-btn-cancel">Cancelar</button>
              <button type="submit" class="btn btn--primary">Salvar Alterações</button>
            </div>
          </form>
        </div>
      </div>
    `;
    document.body.insertAdjacentHTML('beforeend', modalHtml);
  }

  const form = $('inv-edit-form');
  if (!form) return;

  if (window._invEditModalBound) return;
  window._invEditModalBound = true;

  applyDateMask($('inv-edit-vencimento'));
  applyPercentMask($('inv-edit-taxa'));

  $('inv-edit-modal-close')?.addEventListener('click', closeInvEditModal);
  $('inv-edit-btn-cancel')?.addEventListener('click', closeInvEditModal);
  $('inv-edit-modal-overlay')?.addEventListener('click', e => {
    if (e.target === $('inv-edit-modal-overlay')) closeInvEditModal();
  });

  const formatCurrency = (e) => {
    let val = e.target.value.replace(/\D/g, ''); 
    if (val === '') return;
    val = (parseInt(val, 10) / 100).toFixed(2); 
    val = val.replace('.', ',');
    val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.'); 
    e.target.value = val;
  };

  $('inv-edit-preco')?.addEventListener('input', formatCurrency);
  $('inv-edit-custos')?.addEventListener('input', formatCurrency);

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = $('inv-edit-id').value;
    const ativoId = $('inv-edit-ativo-id').value;
    const op = $('inv-edit-op').value;
    
    let novaQtd = 0;
    if (op !== 'DIVIDENDO') {
      novaQtd = parseInvInput($('inv-edit-qtd').value);
      if (isNaN(novaQtd) || novaQtd < 0) { showToast('Quantidade inválida.', 'error'); return; }
    }

    const novoPreco = parseInvInput($('inv-edit-preco').value);
    const novosCustos = parseInvInput($('inv-edit-custos').value);
    
    if (isNaN(novoPreco) || novoPreco < 0) { showToast('Preço inválido.', 'error'); return; }

    const isRF = $('inv-edit-group-renda-fixa') && !$('inv-edit-group-renda-fixa').classList.contains('hidden');
    const dataVencimentoRaw = isRF ? ($('inv-edit-vencimento')?.value || null) : null;
    const dataVencimento = convertDateToIso(dataVencimentoRaw);
    
    if (isRF) {
      if (!dataVencimento || !/^\d{4}-\d{2}-\d{2}$/.test(dataVencimento)) {
        showToast('Informe uma data de vencimento válida (DD/MM/YYYY).', 'error');
        return;
      }
    }

    const indexador = isRF ? ($('inv-edit-indexador')?.value || null) : null;
    const taxaRaw = isRF ? ($('inv-edit-taxa')?.value || null) : null;
    const taxa = taxaRaw ? parseInvInput(taxaRaw) : null;

    try {
      const valorTotal = op === 'DIVIDENDO' ? (novoPreco + novosCustos) : ((novaQtd * novoPreco) + novosCustos);
      await Api.updateInvestimentoLancamento(id, {
        quantidade: novaQtd,
        precoUnitario: novoPreco,
        custos: novosCustos,
        valorTotal: valorTotal,
        dataVencimento,
        indexador,
        taxa
      });
      
      showToast('Lançamento atualizado!', 'success');
      closeInvEditModal();
      
      const lancamentos = await Api.getLancamentosInvestimento(ativoId);
      renderizarTabelaHistorico(lancamentos);
      loadInvestimentos();
    } catch (err) {
      showToast('Erro ao atualizar lançamento: ' + err.message, 'error');
    }
  });
}

// ─── ATUALIZAR COTAÇÕES VIA BRAPI ────────────────────────────────────────

async function atualizarCotacoesBrapi() {
  const btn = $('btn-atualizar-cotacoes');
  if (!btn) return;

  const originalText = btn.innerHTML;
  btn.innerHTML = 'Atualizando...';
  btn.disabled = true;

  try {
    const result = await Api.atualizarCotacoes();
    showToast(result.message, 'success');
    loadInvestimentos();
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    btn.innerHTML = originalText;
    btn.disabled = false;
  }
}

// ─── BRAPI CONFIG ────────────────────────────────────────────────────────

async function checkBrapiStatus() {
  try {
    const status = await Api.getBrapiStatus();
    const el = $('brapi-key-status');
    if (el) {
      el.innerHTML = status.configurado
        ? '<span style="color:var(--brand-glow);">✅ Token BrAPI configurado</span>'
        : '<span style="color:var(--accent-red);">❌ Token BrAPI não configurado</span>';
    }
  } catch (e) { /* silently fail */ }
}

function initBrapiConfig() {
  $('btn-save-brapi-token')?.addEventListener('click', async () => {
    const token = $('input-brapi-token')?.value?.trim();
    if (!token) { showToast('Cole o token do BrAPI.', 'error'); return; }
    try {
      await Api.saveBrapiToken(token);
      showToast('Token BrAPI salvo!', 'success');
      $('input-brapi-token').value = '';
      checkBrapiStatus();
    } catch (err) {
      showToast(err.message, 'error');
    }
  });
}

// ─── UTILS ───────────────────────────────────────────────────────────────

function fmtPercent(value) {
  const v = parseFloat(value || 0);
  const sign = v >= 0 ? '+' : '';
  return `${sign}${v.toFixed(2)}%`;
}

function formatQtd(value) {
  const v = parseFloat(value || 0);
  if (Number.isInteger(v)) return v.toString();
  return v.toFixed(v < 1 ? 8 : 2);
}

// ─── EXPORTAÇÃO ──────────────────────────────────────────────────────────

function downloadInvestimentos(format) {
  const url = `/api/investimentos/export/${format}`;
  const fileName = `investimentos.${format}`;
  
  if (window.javaBridge) {
    window.javaBridge.saveFile(url, fileName, null);
    return;
  }
  
  fetch(url)
    .then(res => {
      if (!res.ok) throw new Error('Falha no download');
      return res.blob();
    })
    .then(blob => {
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = fileName;
      link.click();
    })
    .catch(err => showToast('Erro ao exportar ' + format.toUpperCase(), 'error'));
}

function fmtDate(isoString) {
  if (!isoString) return '-';
  const parts = isoString.split('-');
  if (parts.length === 3) {
    return `${parts[2]}/${parts[1]}/${parts[0]}`;
  }
  return isoString;
}

function formatarTaxaIndexador(taxa, indexador) {
  if (!taxa) return '-';
  if (!indexador) return `${parseFloat(taxa).toFixed(2).replace('.', ',')}% a.a.`;
  const idx = indexador.toUpperCase().trim();
  if (idx === 'CDI') {
    return `${parseFloat(taxa).toFixed(2).replace('.', ',')}% do CDI`;
  }
  if (idx === 'SELIC') {
    return `SELIC + ${parseFloat(taxa).toFixed(2).replace('.', ',')}%`;
  }
  if (idx === 'IPCA') {
    return `IPCA + ${parseFloat(taxa).toFixed(2).replace('.', ',')}%`;
  }
  if (idx === 'PRE') {
    return `${parseFloat(taxa).toFixed(2).replace('.', ',')}% a.a.`;
  }
  return `${parseFloat(taxa).toFixed(2).replace('.', ',')}% ${indexador}`;
}
