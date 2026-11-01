/**
 * investments.js – Entry point e orquestrador da aba de Investimentos do MyTwoCents
 * Módulos dependentes:
 *  - inv-constants.js (constantes de tipos de ativos e categorias táticas)
 *  - inv-charts.js (gráficos de distribuição, evolução e dividendos)
 *  - inv-modal.js (modais de inclusão, edição, cotação e configurações)
 *  - inv-proventos.js (histórico anual/mensal de proventos e ações do modal)
 *  - inv-sniper.js (modo sniper, oportunidades táticas e smart split aporte)
 *  - inv-destaques.js (destaques de rentabilidade e posições no lucro/prejuízo)
 */

// ─── LOAD ────────────────────────────────────────────────────────────────

async function loadInvestimentos() {
  const loader = $('loader-investimentos');
  const view = $('view-investimentos');
  // Show loader: cover the view content area, keep sidebar accessible
  if (loader) loader.classList.remove('hidden');
  document.body.classList.add('loading-active');
  // Hide all children except the loader so nothing is visible behind
  if (view) {
    view.classList.add('is-loading');
    Array.from(view.children).forEach(child => {
      if (child.id !== 'loader-investimentos') child.style.visibility = 'hidden';
    });
  }
  try {
    initInvEditModalListeners();
    const data = await Api.getInvestimentoDashboard();
    renderInvCards(data);
    renderDestaquesECardTatica(data);
    renderInvDistChart(data);
    renderInvEvolucaoChart(data);
    renderInvDividendosChart(data);
    renderInvAcordeoes(data);
    renderProvHistoricoCard();
    renderSniperOverview();

    // Carrega botão/insights da IA para a carteira
    if (typeof loadAiInsights === 'function') {
      loadAiInsights('ai-insights-investimentos');
    }
  } catch (err) {
    console.error('Erro ao carregar investimentos:', err);
    showToast('Erro ao carregar investimentos: ' + err.message, 'error');
  } finally {
    // Reveal content and hide loader
    if (view) {
      Array.from(view.children).forEach(child => {
        if (child.id !== 'loader-investimentos') child.style.visibility = '';
      });
      view.classList.remove('is-loading');
    }
    if (loader) loader.classList.add('hidden');
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
    const temSetorSegmento = (tipo === 'ACAO' || tipo === 'FII' || tipo === 'ETF');
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
              ${isRendaFixa ? '<th>Data Lanç.</th>' : ''}
              ${isRendaFixa ? '' : '<th>Qtd</th>'}
              ${(isRendaFixa || isTesouroDireto) ? '' : '<th>PM</th>'}
              ${isRendaFixa ? '' : '<th>Atual</th>'}
              <th>Var%</th>
              ${(tipo === 'ACAO' || tipo === 'FII') ? '<th>D.Y.</th>' : ''}
              <th>Saldo</th>
              ${temSetorSegmento ? `<th>${labelSetorSegmento}</th>` : ''}
              ${isRendaFixaTesouro ? `<th>Vencimento</th><th>Taxa</th><th>Rend. Mensal</th>` : ''}
              <th>% Cart.</th><th class="col-actions">Ações</th>
            </tr>
          </thead>
          <tbody>
            ${ativos.map(a => {
              const v = parseFloat(a.variacao || 0);
              const vc = v >= 0 ? 'inv-variation--up' : 'inv-variation--down';
              let varTooltipAttr = '';
              const pmVal = parseFloat(a.precoMedio || 0);
              const qtdVal = parseFloat(a.quantidade || 0);
              if (pmVal > 0 && qtdVal > 0) {
                const totalPago = qtdVal * pmVal;
                const totalAtual = parseFloat(a.valorTotal || 0);
                const lp = totalAtual - totalPago;
                varTooltipAttr = `data-lucro="${lp}" data-percent="${v}"`;
              }

              return `<tr>
                <td>
                  <div class="inv-ticker-container" data-tooltip="${escHtml(a.longName || a.nome || a.ticker)}">
                    ${a.logoUrl ? `<img src="${a.logoUrl}" class="inv-ticker-logo" alt="${escHtml(a.ticker)}" onerror="this.style.display='none'" />` : ''}
                    <div>
                      <strong>${escHtml(a.ticker)}</strong>
                      <span style="display: block; font-size: 0.71rem; color: ${CATEGORIA_TATICA_COLORS[(a.categoriaTatica || 'SEGURANCA').toString().toUpperCase()] || '#34d399'}; font-weight: 600; margin-top: 1px;">${escHtml(CATEGORIA_TATICA_LABELS[(a.categoriaTatica || 'SEGURANCA').toString().toUpperCase()] || a.categoriaTatica || 'Segurança')}</span>
                    </div>
                  </div>
                </td>
                ${isRendaFixa ? `<td>${a.dataLancamento ? fmtDate(a.dataLancamento) : '-'}</td>` : ''}
                ${isRendaFixa ? '' : `<td>${formatQtd(a.quantidade)}</td>`}
                ${(isRendaFixa || isTesouroDireto) ? '' : `<td>${fmtCurrency(parseFloat(a.precoMedio || 0))}</td>`}
                ${isRendaFixa ? '' : `<td>${fmtCurrency(parseFloat(a.precoAtual || 0))}</td>`}
                <td class="${vc} ${varTooltipAttr ? 'var-cell-clickable' : ''}" ${varTooltipAttr}>${fmtPercent(v)}</td>
                ${(tipo === 'ACAO' || tipo === 'FII') ? `<td style="font-weight: 500; color: var(--brand-glow);">${a.dy != null && parseFloat(a.dy) > 0 ? parseFloat(a.dy).toFixed(2) + '%' : '-'}</td>` : ''}
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
                    <button class="action-btn action-btn--edit" title="Ver Histórico" onclick="abrirHistoricoAtivo(${a.id}, '${escHtml(a.ticker)}', ${a.precoAtual || 0}, '${tipo}')">
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
      </div>
    `;
  }
  initVarCellTooltips();
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

window.loadInvestimentos = loadInvestimentos;
