/**
 * inv-destaques.js – Destaques da carteira, posições no lucro/prejuízo e tabela paginada
 * Depende de: inv-constants.js, inv-charts.js (renderMiniTaticaChart)
 */

// ─── DESTAQUES & MODAL POSIÇÕES NO LUCRO / PREJUÍZO ─────────────────────
let _lucroPrejuizoState = {
  currentTab: 'lucro',
  page: 1,
  pageSize: 5,
  lucroList: [],
  prejuizoList: []
};

function renderDestaquesECardTatica(data) {
  const allAtivos = [];
  if (data && data.ativosPorTipo) {
    Object.values(data.ativosPorTipo).forEach(lista => {
      (lista || []).forEach(a => {
        const qtd = parseFloat(a.quantidade || 0);
        const pm = parseFloat(a.precoMedio || 0);
        const precoAtual = parseFloat(a.precoAtual || 0);
        const valorTotal = parseFloat(a.valorTotal || (qtd * precoAtual));
        const valorInvestido = qtd * pm;
        
        let lucro = valorTotal - valorInvestido;
        let rentabilidade = 0;
        if (valorInvestido > 0) {
          rentabilidade = (lucro / valorInvestido) * 100;
        } else {
          rentabilidade = parseFloat(a.variacao || 0);
        }

        allAtivos.push({
          ...a,
          valorTotal,
          valorInvestido,
          lucro,
          rentabilidade
        });
      });
    });
  }

  if (allAtivos.length === 0) return;

  const sorted = [...allAtivos].sort((a, b) => b.rentabilidade - a.rentabilidade);
  const melhor = sorted[0];
  const pior = sorted[sorted.length - 1];

  const lucroList = sorted.filter(a => a.rentabilidade >= 0);
  const prejuizoList = sorted.filter(a => a.rentabilidade < 0).reverse();

  _lucroPrejuizoState.lucroList = lucroList;
  _lucroPrejuizoState.prejuizoList = prejuizoList;

  // 1. Atualiza Card 1 (Destaques da carteira)
  if (melhor && document.getElementById('destaque-melhor-ticker')) {
    document.getElementById('destaque-melhor-ticker').textContent = melhor.ticker;
    const bMelhor = document.getElementById('destaque-melhor-badge');
    if (bMelhor) {
      bMelhor.textContent = `${melhor.rentabilidade >= 0 ? '+' : ''}${melhor.rentabilidade.toFixed(2).replace('.', ',')}% ↗`;
      bMelhor.className = `badge-pill ${melhor.rentabilidade >= 0 ? 'badge-pill--up' : 'badge-pill--down'}`;
    }
  }

  if (pior && document.getElementById('destaque-pior-ticker')) {
    document.getElementById('destaque-pior-ticker').textContent = pior.ticker;
    const bPior = document.getElementById('destaque-pior-badge');
    if (bPior) {
      bPior.textContent = `${pior.rentabilidade >= 0 ? '+' : ''}${pior.rentabilidade.toFixed(2).replace('.', ',')}% ↘`;
      bPior.className = `badge-pill ${pior.rentabilidade >= 0 ? 'badge-pill--up' : 'badge-pill--down'}`;
    }
  }

  // 2. Atualiza Card 2 (Posições no lucro)
  const totalCount = allAtivos.length;
  const lucroCount = lucroList.length;
  const prejuizoCount = prejuizoList.length;
  const pctLucro = totalCount > 0 ? (lucroCount / totalCount) * 100 : 0;

  if (document.getElementById('lucro-metric-count')) document.getElementById('lucro-metric-count').textContent = `${lucroCount} de ${totalCount}`;
  if (document.getElementById('lucro-metric-pct')) document.getElementById('lucro-metric-pct').textContent = `${pctLucro.toFixed(0)}%`;
  if (document.getElementById('lucro-progress-fill')) document.getElementById('lucro-progress-fill').style.width = `${pctLucro}%`;
  if (document.getElementById('lucro-sub-count')) document.getElementById('lucro-sub-count').textContent = `${lucroCount} no lucro`;
  if (document.getElementById('prejuizo-sub-count')) document.getElementById('prejuizo-sub-count').textContent = `${prejuizoCount} no prejuízo`;

  if (document.getElementById('modal-toggle-lucro-count')) document.getElementById('modal-toggle-lucro-count').textContent = `${lucroCount} no lucro`;
  if (document.getElementById('modal-toggle-prejuizo-count')) document.getElementById('modal-toggle-prejuizo-count').textContent = `${prejuizoCount} no prejuízo`;

  // 3. Mini Chart de Categoria Tática
  renderMiniTaticaChart(allAtivos);
}

window.openLucroPrejuizoModal = function(tab = 'lucro') {
  _lucroPrejuizoState.currentTab = tab;
  _lucroPrejuizoState.page = 1;
  switchLucroPrejuizoTab(tab);
  document.getElementById('modal-lucro-prejuizo-overlay')?.classList.remove('hidden');
  document.getElementById('main-content')?.setAttribute('aria-hidden', 'true');
};

window.closeLucroPrejuizoModal = function() {
  document.getElementById('modal-lucro-prejuizo-overlay')?.classList.add('hidden');
  document.getElementById('main-content')?.removeAttribute('aria-hidden');
};

window.switchLucroPrejuizoTab = function(tab) {
  _lucroPrejuizoState.currentTab = tab;
  _lucroPrejuizoState.page = 1;

  const btnLucro = document.getElementById('modal-toggle-lucro');
  const btnPrejuizo = document.getElementById('modal-toggle-prejuizo');
  const modalTitle = document.getElementById('modal-lucro-prejuizo-title');

  if (tab === 'lucro') {
    if (btnLucro) btnLucro.className = 'modal-toggle-btn active-lucro';
    if (btnPrejuizo) btnPrejuizo.className = 'modal-toggle-btn';
    if (modalTitle) modalTitle.textContent = 'Posições no lucro';
  } else {
    if (btnLucro) btnLucro.className = 'modal-toggle-btn';
    if (btnPrejuizo) btnPrejuizo.className = 'modal-toggle-btn active-prejuizo';
    if (modalTitle) modalTitle.textContent = 'Posições no prejuízo';
  }

  renderLucroPrejuizoTable();
};

function renderLucroPrejuizoTable() {
  const tbody = document.getElementById('modal-lucro-prejuizo-tbody');
  const paginationEl = document.getElementById('modal-lucro-pagination');
  if (!tbody) return;

  const isLucro = _lucroPrejuizoState.currentTab === 'lucro';
  const list = isLucro ? _lucroPrejuizoState.lucroList : _lucroPrejuizoState.prejuizoList;

  if (list.length === 0) {
    tbody.innerHTML = `<tr><td colspan="4" style="text-align:center;color:var(--text-muted);padding:2rem;">Nenhuma posição no ${isLucro ? 'lucro' : 'prejuízo'} registrada.</td></tr>`;
    if (paginationEl) paginationEl.innerHTML = '';
    return;
  }

  const page = _lucroPrejuizoState.page;
  const pageSize = _lucroPrejuizoState.pageSize;
  const totalPages = Math.ceil(list.length / pageSize);
  const startIndex = (page - 1) * pageSize;
  const pageItems = list.slice(startIndex, startIndex + pageSize);

  tbody.innerHTML = pageItems.map(a => {
    const isUp = a.rentabilidade >= 0;
    const rClass = isUp ? 'badge-pill--up' : 'badge-pill--down';
    const vClass = (a.variacao || 0) >= 0 ? 'inv-variation--up' : 'inv-variation--down';
    const arrow = isUp ? '↗' : '↘';

    return `
      <tr style="border-bottom: 1px solid rgba(255,255,255,0.05);">
        <td style="padding: 0.75rem 0.5rem;">
          <div class="inv-ticker-container">
            ${a.logoUrl ? `<img src="${a.logoUrl}" class="inv-ticker-logo" alt="${escHtml(a.ticker)}" onerror="this.style.display='none'" />` : ''}
            <div>
              <strong>${escHtml(a.ticker)}</strong>
              ${a.nome ? `<span style="display:block;font-size:0.75rem;color:var(--text-muted);">${escHtml(a.nome)}</span>` : ''}
            </div>
          </div>
        </td>
        <td style="text-align: right; font-weight: 600; padding: 0.75rem 0.5rem;">${fmtCurrency(a.valorTotal || 0)}</td>
        <td style="text-align: right; padding: 0.75rem 0.5rem;" class="${vClass}">${fmtPercent(a.variacao || 0)}</td>
        <td style="text-align: right; padding: 0.75rem 0.5rem;">
          <span class="badge-pill ${rClass}">
            ${isUp ? '+' : ''}${a.rentabilidade.toFixed(2).replace('.', ',')}% ${arrow}
          </span>
        </td>
      </tr>
    `;
  }).join('');

  if (paginationEl) {
    if (totalPages <= 1) {
      paginationEl.innerHTML = '';
      return;
    }

    let pPrev = `<button class="btn btn--secondary btn--sm" ${page <= 1 ? 'disabled' : ''} onclick="changeLucroPage(${page - 1})">Anterior</button>`;
    let pNext = `<button class="btn btn--secondary btn--sm" ${page >= totalPages ? 'disabled' : ''} onclick="changeLucroPage(${page + 1})">Próximo</button>`;

    let pNums = '';
    for (let p = 1; p <= totalPages; p++) {
      pNums += `<button class="btn btn--sm ${p === page ? 'btn--primary' : 'btn--secondary'}" onclick="changeLucroPage(${p})">${p}</button>`;
    }

    paginationEl.innerHTML = `${pPrev} ${pNums} ${pNext}`;
  }
}

window.changeLucroPage = function(page) {
  _lucroPrejuizoState.page = page;
  renderLucroPrejuizoTable();
};
