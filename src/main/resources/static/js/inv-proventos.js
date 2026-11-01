/**
 * inv-proventos.js – Histórico de proventos da carteira e modais correspondentes
 * Depende de: inv-constants.js
 */

// ─── HISTÓRICO DE PROVENTOS ──────────────────────────────────────────────

async function renderProvHistoricoCard() {
  const tbody = $('prov-hist-tbody');
  const totalEl = $('prov-total-value');
  if (!tbody) return;

  try {
    const data = await Api.getProventosHistorico();
    window._proventosHistoricoData = data;

    const total = parseFloat(data.total || 0);
    const totalLanc = (data.lancamentos || []).length;

    const countEl = $('prov-count-label');
    if (countEl) countEl.textContent = `${totalLanc} lançamento(s)`;

    if (totalEl) {
      totalEl.textContent = fmtCurrency(total);
      const tooltip = $('prov-tipo-tooltip');
      if (tooltip && data.porTipo) {
        tooltip.innerHTML = buildTipoTooltipHtml(data.porTipo, 'Por tipo de ativo');
        totalEl.addEventListener('mouseenter', () => tooltip.classList.remove('hidden'));
        totalEl.addEventListener('mouseleave', () => tooltip.classList.add('hidden'));
      }
    }

    const rows = data.porAno || [];
    if (rows.length === 0) {
      tbody.innerHTML = '<tr><td colspan="15" style="text-align:center;color:var(--text-muted);padding:1.5rem;">Nenhum provento lançado ainda.</td></tr>';
      return;
    }

    const fmtVal = (v) => {
      const n = parseFloat(v || 0);
      if (n === 0) return '<span style="color:var(--text-muted);">-</span>';
      return fmtCurrency(n).replace('R$\u00a0', '').replace('R$ ', '');
    };

    tbody.innerHTML = '';
    let somaTotalGeral = 0;
    rows.forEach(row => {
      somaTotalGeral += parseFloat(row.total || 0);
      const tr = document.createElement('tr');
      const mesCells = [1,2,3,4,5,6,7,8,9,10,11,12].map(m => {
        const val = parseFloat(row['m' + m] || 0);
        const tipoBreakdown = row['m' + m + 'Tipo'] || {};
        if (val === 0) return `<td><span style="color:var(--text-muted);">-</span></td>`;
        const tooltipHtml = buildTipoTooltipHtml(tipoBreakdown, null).replace(/"/g, '&quot;');
        return `<td><span class="prov-cell-clickable"
          data-ano="${row.ano}" data-mes="${m}" data-tipo-html="${tooltipHtml}"
          onclick="abrirModalProventosMes(${row.ano},${m})"
          style="cursor:pointer;color:var(--brand-glow);font-weight:600;"
        >${fmtVal(val)}</span></td>`;
      }).join('');
      tr.innerHTML = `
        <td style="font-weight:700;color:var(--text-primary);">${row.ano}</td>
        ${mesCells}
        <td style="color:var(--text-secondary);font-style:italic;">${fmtVal(row.media)}</td>
        <td style="font-weight:700;color:#a78bfa;">${fmtVal(row.total)}</td>
      `;
      tbody.appendChild(tr);
    });

    // Linha com o Total Histórico somando o valor da coluna Total de cada ano
    const trTotalGeral = document.createElement('tr');
    trTotalGeral.style.borderTop = '2px solid var(--border)';
    trTotalGeral.style.background = 'rgba(167, 139, 250, 0.05)';
    trTotalGeral.innerHTML = `
      <td colspan="14" style="font-weight:700;color:var(--text-primary);text-align:right;padding-right:1.5rem;">Total Histórico</td>
      <td style="font-weight:800;color:#a78bfa;font-size:1.05rem;">${fmtVal(somaTotalGeral)}</td>
    `;
    tbody.appendChild(trTotalGeral);

    initProvCellTooltips();

  } catch(err) {
    console.error(err);
    if (tbody) tbody.innerHTML = '<tr><td colspan="15" style="text-align:center;color:var(--accent-red);padding:1rem;">Erro ao carregar histórico de proventos.</td></tr>';
  }
}

function buildTipoTooltipHtml(tipoBreakdown, title) {
  const tipoLabels = { ACAO: 'Ações', FII: 'FIIs', ETF: 'ETFs', RENDA_FIXA: 'Renda Fixa', TESOURO_DIRETO: 'Tesouro Direto', CRIPTO: 'Cripto' };
  const tipoColors = { ACAO: '#60a5fa', FII: '#fb923c', ETF: '#a78bfa', RENDA_FIXA: '#34d399', TESOURO_DIRETO: '#22d3ee', CRIPTO: '#fbbf24' };
  let html = title ? `<div style="font-size:0.72rem;font-weight:600;color:var(--text-muted);margin-bottom:0.4rem;">${title}</div>` : '';
  for (const [tipo, val] of Object.entries(tipoBreakdown)) {
    const cor = tipoColors[tipo] || '#94a3b8';
    const label = tipoLabels[tipo] || tipo;
    html += `<div style="display:flex;justify-content:space-between;gap:0.75rem;align-items:center;margin-bottom:0.25rem;">
      <span style="color:${cor};font-size:0.78rem;">● ${label}</span>
      <span style="font-weight:600;font-size:0.8rem;">${fmtCurrency(parseFloat(val))}</span>
    </div>`;
  }
  return html;
}

let _provCellTooltip = null;
function initProvCellTooltips() {
  if (!_provCellTooltip) {
    _provCellTooltip = document.createElement('div');
    _provCellTooltip.className = 'prov-cell-tooltip hidden';
    _provCellTooltip.style.cssText = 'position:fixed;z-index:9999;pointer-events:none;';
    document.body.appendChild(_provCellTooltip);
  }
  document.querySelectorAll('.prov-cell-clickable').forEach(el => {
    el.addEventListener('mouseenter', () => {
      const html = el.getAttribute('data-tipo-html').replace(/&quot;/g, '"');
      if (!html.trim()) return;
      _provCellTooltip.innerHTML = html;
      _provCellTooltip.classList.remove('hidden');
      const rect = el.getBoundingClientRect();
      _provCellTooltip.style.top = (rect.bottom + 6) + 'px';
      _provCellTooltip.style.left = Math.max(8, rect.left - 80) + 'px';
    });
    el.addEventListener('mouseleave', () => _provCellTooltip.classList.add('hidden'));
  });
}

let _varCellTooltip = null;
function initVarCellTooltips() {
  if (!_varCellTooltip) {
    _varCellTooltip = document.createElement('div');
    _varCellTooltip.className = 'prov-cell-tooltip hidden';
    _varCellTooltip.style.cssText = 'position:fixed;z-index:9999;pointer-events:none;';
    document.body.appendChild(_varCellTooltip);
  }
  document.querySelectorAll('.var-cell-clickable').forEach(el => {
    el.addEventListener('mouseenter', () => {
      const lp = parseFloat(el.getAttribute('data-lucro') || 0);
      const isPositive = lp >= 0;
      
      const borderColor = isPositive ? '#34d399' : '#f87171';
      const textColor = isPositive ? '#34d399' : '#f87171';
      const sign = isPositive ? '+' : '';
      
      _varCellTooltip.style.borderColor = borderColor;
      _varCellTooltip.style.borderWidth = '1.5px';
      _varCellTooltip.innerHTML = `
        <div style="font-size:0.75rem;color:var(--text-secondary);font-weight:500;margin-bottom:0.15rem;">Lucro / Prejuízo</div>
        <div style="font-size:0.85rem;font-weight:600;color:${textColor};">${sign}${fmtCurrency(lp)}</div>
      `;
      _varCellTooltip.classList.remove('hidden');
      
      const rect = el.getBoundingClientRect();
      _varCellTooltip.style.top = (rect.bottom + 6) + 'px';
      _varCellTooltip.style.left = Math.max(8, rect.left - 40) + 'px';
    });
    el.addEventListener('mouseleave', () => {
      _varCellTooltip.classList.add('hidden');
      _varCellTooltip.style.borderColor = ''; // reset
      _varCellTooltip.style.borderWidth = '';
    });
  });
}

function abrirModalProventosMes(ano, mes) {
  window._activeProventosFilter = { type: 'mes', ano, mes };
  const data = window._proventosHistoricoData;
  if (!data) return;
  const nomeMes = ['Janeiro','Fevereiro','Março','Abril','Maio','Junho','Julho','Agosto','Setembro','Outubro','Novembro','Dezembro'][mes - 1];
  const filtrados = (data.lancamentos || []).filter(l => {
    if (!l.data) return false;
    const [y, m] = l.data.split('-').map(Number);
    return y === ano && m === mes;
  }).slice().reverse();
  abrirModalProventosComLista(filtrados, `Proventos – ${nomeMes}/${ano}`);
}

function abrirModalProventosComLista(lancamentos, titulo) {
  let overlay = $('prov-modal-overlay');
  if (!overlay) {
    overlay = document.createElement('div');
    overlay.id = 'prov-modal-overlay';
    overlay.className = 'modal-overlay hidden';
    overlay.style.zIndex = '2500';
    overlay.innerHTML = `
      <div class="modal modal--large" role="dialog" aria-modal="true" style="max-width:980px;">
        <div class="modal-header">
          <h2 class="modal-title" id="prov-modal-titulo">Proventos</h2>
          <button class="modal-close" id="prov-modal-close" aria-label="Fechar">✕</button>
        </div>
        <div class="modal-body" style="padding:1.5rem;max-height:70vh;overflow-y:auto;">
          <table class="data-table">
            <thead><tr>
              <th>Data</th><th>Ativo</th><th>Tipo Ativo</th><th>Tipo Pag.</th>
              <th>Cotas</th><th>Valor/Cota</th><th>Valor Bruto</th>
              <th style="color:var(--brand-glow);">Total Líquido</th>
              <th class="col-actions">Ações</th>
            </tr></thead>
            <tbody id="prov-modal-tbody"></tbody>
          </table>
          <div id="prov-modal-empty" class="empty-state hidden" style="padding:2rem;text-align:center;"><p>Nenhum provento encontrado.</p></div>
        </div>
      </div>`;
    document.body.appendChild(overlay);
    $('prov-modal-close').addEventListener('click', () => { overlay.classList.add('hidden'); $('main-content').removeAttribute('aria-hidden'); });
    overlay.addEventListener('click', e => { if (e.target === overlay) $('prov-modal-close').click(); });
  }

  $('prov-modal-titulo').textContent = titulo;
  const tbody = $('prov-modal-tbody');
  const tipoLabels = { ACAO: 'Ações', FII: 'FIIs', ETF: 'ETFs', RENDA_FIXA: 'Renda Fixa', TESOURO_DIRETO: 'Tesouro Direto', CRIPTO: 'Cripto' };
  tbody.innerHTML = '';

  if (!lancamentos.length) {
    $('prov-modal-empty').classList.remove('hidden');
  } else {
    $('prov-modal-empty').classList.add('hidden');
    window._currentLancamentos = lancamentos;
    lancamentos.forEach(l => {
      const parts = l.data ? l.data.split('-') : [];
      const dataFmt = parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : (l.data || '-');
      const liqVal = l.valorLiquido != null ? l.valorLiquido : l.valorTotal;
      const tr = document.createElement('tr');
      
      const hasQtd = l.quantidade != null && parseFloat(l.quantidade) > 0;
      const hasUnit = l.precoUnitario != null && parseFloat(l.precoUnitario) > 0;
      
      const qtdText = hasQtd ? parseFloat(l.quantidade).toFixed(4).replace(/\.?0+$/, '') : '-';
      const unitText = hasUnit ? fmtCurrency(parseFloat(l.precoUnitario)) : '-';
      let tpFmt = l.tipoProvento || 'Dividendo';
      if (tpFmt.toLowerCase().startsWith('div')) tpFmt = 'Dividendo';
      else if (tpFmt.toLowerCase().includes('jcp') || tpFmt.toLowerCase().includes('jscp')) tpFmt = 'JSCP';
      else if (tpFmt.toLowerCase().includes('rend')) tpFmt = 'Rend. Trib.';
      const badgeCls = tpFmt.toLowerCase().replace(/[^a-z0-9]/g, '');

      tr.innerHTML = `
        <td>${dataFmt}</td>
        <td><strong>${escHtml(l.ticker)}</strong></td>
        <td><span style="font-size:0.75rem;color:var(--text-muted);">${tipoLabels[l.tipoAtivo] || l.tipoAtivo || '-'}</span></td>
        <td><span class="ai-cat-badge ai-cat-badge--${badgeCls}" style="font-size:0.75rem;font-weight:600;padding:2px 6px;border-radius:4px;">${tpFmt}</span></td>
        <td>${qtdText}</td>
        <td class="cell-valor">${unitText}</td>
        <td>${fmtCurrency(parseFloat(l.valorTotal || 0))}</td>
        <td><strong style="color:var(--brand-glow);">${fmtCurrency(parseFloat(liqVal || 0))}</strong></td>
        <td class="col-actions">
          <div class="action-btns" style="justify-content:flex-start;gap:0.5rem;">
            <button class="action-btn action-btn--edit" title="Editar" onclick="editarLancamentoDoModal(${l.id})">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg>
            </button>
            <button class="action-btn action-btn--delete" title="Excluir" onclick="excluirProventoDoModal(${l.id},'${escHtml(l.ticker)}')">
              <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#f87171" stroke-width="2"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/></svg>
            </button>
          </div>
        </td>`;
      tbody.appendChild(tr);
    });
  }
  overlay.classList.remove('hidden');
  $('main-content').setAttribute('aria-hidden', 'true');
}

async function editarLancamentoDoModal(id) {
  // Re-use the existing edit modal
  await editarLancamentoInvestimento(id);
  // After edit, refresh proventos modal and card
  const form = $('inv-edit-form');
  if (form && !form._proventosHooked) {
    form._proventosHooked = true;
    form.addEventListener('inv-saved', async () => {
      await renderProvHistoricoCard();
      // Reopen the proventos modal with fresh data
      const overlay = $('prov-modal-overlay');
      if (overlay && !overlay.classList.contains('hidden')) {
        setTimeout(() => {
          if (window._activeProventosFilter && window._activeProventosFilter.type === 'mes') {
             abrirModalProventosMes(window._activeProventosFilter.ano, window._activeProventosFilter.mes);
          }
        }, 600);
      }
    }, { once: false });
  }
}

async function excluirProventoDoModal(lancamentoId, ticker) {
  if (!confirm(`Excluir este provento de ${ticker}?`)) return;
  try {
    await Api.excluirLancamentoInvestimento(lancamentoId);
    showToast('Provento excluído!', 'success');
    await renderProvHistoricoCard();
    if (window._activeProventosFilter && window._activeProventosFilter.type === 'mes') {
       abrirModalProventosMes(window._activeProventosFilter.ano, window._activeProventosFilter.mes);
    }
    loadInvestimentos();
  } catch (err) {
    showToast('Erro ao excluir: ' + err.message, 'error');
  }
}
