/**
 * inv-sniper.js – Sniper mode, Smart Split aporte e metas por tipo de ativo
 * Depende de: inv-constants.js
 */

// ─── SNIPER MODE & SMART SPLIT APORTE ────────────────────────────────────

async function renderSniperOverview() {
  try {
    const inputAporte = $('input-aporte-valor');
    if (inputAporte && !inputAporte.dataset.maskBound) {
      inputAporte.dataset.maskBound = 'true';
      inputAporte.addEventListener('input', (e) => {
        let val = e.target.value.replace(/\D/g, '');
        if (val === '') {
          e.target.value = '';
          return;
        }
        val = (parseInt(val, 10) / 100).toFixed(2);
        val = val.replace('.', ',');
        val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.');
        e.target.value = val;
      });
    }

    const overview = await Api.getSniperOverview();
    
    // Atualiza Cadeado de Segurança
    const badgeEl = $('em-lock-badge');
    const descEl = $('em-lock-desc');
    const valuesEl = $('em-lock-values');
    const barEl = $('em-lock-bar');
    const cardEl = $('sniper-emergency-lock-card');
    const selicBadge = $('selic-rate-badge');

    const totSeg = parseFloat(overview.totalSeguranca || 0);
    const targetBox = parseFloat(overview.emergencyBoxTarget || 20000);
    const pctSeg = parseFloat(overview.pctSeguranca || 0);
    const faltante = parseFloat(overview.valorFaltanteSeguranca || 0);
    const selicNum = parseFloat(overview.taxaSelic || 13.75);
    const selicVal = selicNum.toFixed(2);
    const cdiVal = parseFloat(overview.taxaCdi || (selicNum - 0.10)).toFixed(2);
    const ipcaVal = parseFloat(overview.taxaIpca || 4.22).toFixed(2);

    if (selicBadge) {
      selicBadge.textContent = `⚡ Selic: ${selicVal.replace('.', ',')}% | CDI: ${cdiVal.replace('.', ',')}% | IPCA: ${ipcaVal.replace('.', ',')}%`;
    }

    const pctMeta = targetBox > 0 ? Math.min(100, (totSeg / targetBox) * 100) : 0;

    if (valuesEl) {
      valuesEl.textContent = `${fmtCurrency(totSeg)} / ${fmtCurrency(targetBox)} (${pctMeta.toFixed(0)}% da meta | ${pctSeg.toFixed(1)}% do patrimônio)`;
    }

    if (barEl) {
      barEl.style.width = `${pctMeta}%`;
      barEl.style.background = overview.emergencyLock ? '#ef4444' : '#10b981';
    }

    if (overview.emergencyLock) {
      if (cardEl) {
        cardEl.className = 'card card--red';
        cardEl.style.borderLeft = 'none';
      }
      if (badgeEl) {
        badgeEl.textContent = 'CADEADO ATIVO';
        badgeEl.style.background = '#ef444420';
        badgeEl.style.color = '#ef4444';
      }
      if (descEl) {
        descEl.textContent = `Atenção: Reserva de emergência abaixo da meta! Faltam ${fmtCurrency(faltante)} para atingir a meta financeira de segurança. Novos aportes serão direcionados para o caixa de emergência.`;
      }
    } else {
      if (cardEl) {
        cardEl.className = 'card card--green';
        cardEl.style.borderLeft = 'none';
      }
      if (badgeEl) {
        badgeEl.textContent = 'RESERVA SEGURA';
        badgeEl.style.background = '#10b98120';
        badgeEl.style.color = '#10b981';
      }
      if (descEl) {
        descEl.textContent = 'Reserva de Emergência segura e dentro da meta estipulada. Aportes liberados para alocação proporcional na carteira.';
      }
    }

    // Renderiza oportunidades táticas
    renderSniperOpportunitiesList(overview.oportunidades || []);

  } catch (err) {
    console.error('Erro ao carregar Sniper Overview:', err);
  }
}

async function renderSniperOpportunities() {
  try {
    const ops = await Api.getTacticalOpportunities();
    renderSniperOpportunitiesList(ops);
  } catch (err) {
    showToast('Erro ao carregar oportunidades táticas: ' + err.message, 'error');
  }
}

function renderSniperOpportunitiesList(ops) {
  const countEl = $('sniper-ops-count');
  const container = $('sniper-ops-container');
  if (!container) return;

  if (countEl) countEl.textContent = `${ops.length} Gatilhos`;

  if (!ops || ops.length === 0) {
    container.innerHTML = '<p style="text-align: left; color: var(--text-muted); font-size: 0.85rem; padding: 1.5rem;">Nenhum gatilho tático no momento. Todos os ativos cíclicos estão dentro de variações normais de preço médio.</p>';
    return;
  }

  container.innerHTML = ops.map(op => {
    const isCompra = op.tipoGatilho === 'COMPRA';
    const color = isCompra ? '#10b981' : '#ef4444';
    const icon = isCompra ? '📉' : '📈';
    const varPct = parseFloat(op.variacaoPercent || 0);

    return `
      <div style="background: var(--bg-hover, rgba(255,255,255,0.03)); border: 1px solid var(--border); border-left: 4px solid ${color}; border-radius: var(--radius-sm, 6px); padding: 0.85rem; margin-bottom: 0.75rem;">
        <div>
          <div style="display: flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
            <strong style="font-size: 1rem;">${escHtml(op.ticker)}</strong>
            <span class="inv-badge" style="background:${color}20; color:${color}; font-size: 0.75rem;">${icon} ${isCompra ? 'COMPRA' : 'VENDA'} NÍVEL ${op.nivelGatilho}</span>
            <span style="font-size: 0.8rem; font-weight: 600; color: ${color};">${varPct > 0 ? '+' : ''}${varPct.toFixed(2)}% vs PM</span>
          </div>
          <p style="margin: 0.35rem 0 0; font-size: 0.82rem; color: var(--text-secondary); line-height: 1.3;">
            ${escHtml(op.sugestaoAcao)}
          </p>
          <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.25rem;">
            Preço Atual: ${fmtCurrency(parseFloat(op.precoAtual || 0))} | PM: ${fmtCurrency(parseFloat(op.precoMedio || 0))} | Sugestão: ${Math.round(parseFloat(op.sugestaoQuantidade || 0))} cotas
          </div>
        </div>
      </div>
    `;
  }).join('');
}

async function calcularAporteSmartSplitUI() {
  const inputEl = $('input-aporte-valor');
  const container = $('aporte-resultado-container');
  if (!inputEl || !container) return;

  let rawDigits = inputEl.value.replace(/\D/g, '');
  if (!rawDigits || rawDigits === '') {
    showToast('Informe um valor de aporte válido', 'warning');
    return;
  }
  const val = parseInt(rawDigits, 10) / 100;
  if (!val || isNaN(val) || val <= 0) {
    showToast('Informe um valor de aporte válido', 'warning');
    return;
  }

  try {
    container.innerHTML = '<p style="text-align:center; padding: 1rem; color: var(--text-muted);">Calculando alocação inteligente...</p>';
    const res = await Api.calcularAporteSmartSplit(val);

    if (!res.itens || res.itens.length === 0) {
      container.innerHTML = `<p style="text-align:center; padding: 1rem; color: var(--text-muted);">${res.mensagemLock || 'Nenhum ativo elegível para receber aportes no momento.'}</p>`;
      return;
    }

    let html = '';
    if (res.emergencyLockAtivo && res.mensagemLock) {
      html += `<div style="background: #ef444415; border: 1px solid #ef444440; color: #f87171; padding: 0.6rem 0.8rem; border-radius: 6px; font-size: 0.8rem; margin-bottom: 0.75rem;">
        🔒 ${escHtml(res.mensagemLock)}
      </div>`;
    }

    html += `<table class="data-table inv-table" style="font-size: 0.82rem;">
      <thead>
        <tr>
          <th>Ticker</th>
          <th>Cotas Est.</th>
          <th>Preço</th>
          <th>Valor Alocado</th>
          <th>% Aporte</th>
        </tr>
      </thead>
      <tbody>
        ${res.itens.map(item => {
          const tipo = item.tipoAtivo || '';
          const cotas = parseFloat(item.cotasEstimadas || 0);
          // Exibe frações para CRIPTO, RENDA_FIXA e TESOURO_DIRETO; inteiro para os demais
          const fracionavel = tipo === 'CRIPTO' || tipo === 'RENDA_FIXA' || tipo === 'TESOURO_DIRETO';
          const cotasStr = fracionavel ? cotas.toFixed(4) : Math.floor(cotas).toString();
          return `
          <tr>
            <td>
              <strong>${escHtml(item.ticker)}</strong>
              ${item.ativoSeguranca ? '<span class="inv-badge" style="background:#10b98120;color:#10b981;font-size:0.65rem;margin-left:0.25rem;">Segurança</span>' : ''}
            </td>
            <td>${cotasStr}</td>
            <td>${fmtCurrency(parseFloat(item.precoAtual || 0))}</td>
            <td style="font-weight: 700; color: var(--accent-green, #10b981);">${fmtCurrency(parseFloat(item.valorAlocado || 0))}</td>
            <td>${parseFloat(item.percentualAporte || 0).toFixed(1)}%</td>
          </tr>
        `}).join('')}
      </tbody>
    </table>
    <div style="font-size: 0.75rem; color: var(--text-muted); margin-top: 0.5rem; text-align: right;">
      Aporte Efetivo: <strong>${fmtCurrency(parseFloat(res.valorAporteEfetivo || 0))}</strong> | Sobra Caixa: <strong>${fmtCurrency(parseFloat(res.sobraCaixa || 0))}</strong>
    </div>`;

    container.innerHTML = html;

  } catch (err) {
    showToast('Erro ao calcular aporte: ' + err.message, 'error');
    container.innerHTML = '';
  }
}

function openEmergencyConfigModal() {
  const metaEl = $('input-meta-emergencia');
  const rendaEl = $('input-renda-mensal');

  const bindMask = (el) => {
    if (!el || el.dataset.maskBound) return;
    el.dataset.maskBound = 'true';
    el.addEventListener('input', (e) => {
      let val = e.target.value.replace(/\D/g, '');
      if (val === '') {
        e.target.value = '';
        return;
      }
      val = (parseInt(val, 10) / 100).toFixed(2);
      val = val.replace('.', ',');
      val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.');
      e.target.value = val;
    });
  };

  bindMask(metaEl);
  bindMask(rendaEl);

  const formatVal = (n) => {
    if (!n && n !== 0) return '';
    const num = parseFloat(n);
    if (isNaN(num)) return '';
    let val = num.toFixed(2);
    val = val.replace('.', ',');
    val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.');
    return val;
  };

  Api.getSniperOverview().then(ov => {
    if (metaEl) metaEl.value = formatVal(ov.emergencyBoxTarget || 20000);
    if (rendaEl) rendaEl.value = formatVal(ov.monthlyIncome || 5000);
    $('modal-em-config-overlay')?.classList.remove('hidden');
  }).catch(() => {
    $('modal-em-config-overlay')?.classList.remove('hidden');
  });
}

function closeEmergencyConfigModal() {
  $('modal-em-config-overlay')?.classList.add('hidden');
}

async function salvarConfigEmergenciaUI() {
  const metaRaw = $('input-meta-emergencia')?.value.replace(/\D/g, '');
  const rendaRaw = $('input-renda-mensal')?.value.replace(/\D/g, '');

  const targetBox = metaRaw ? (parseInt(metaRaw, 10) / 100) : 20000;
  const income = rendaRaw ? (parseInt(rendaRaw, 10) / 100) : 5000;

  try {
    await Api.updateConfigEmergencia(targetBox, income);
    showToast('Configuração da Caixa de Emergência salva com sucesso!', 'success');
    closeEmergencyConfigModal();
    renderSniperOverview();
  } catch (err) {
    showToast('Erro ao salvar configuração: ' + err.message, 'error');
  }
}

function quickFillOperacao(ticker, tipoOperacao, quantidade, precoAtual) {
  openInvModal();
  setTimeout(() => {
    if ($('inv-form-ticker')) $('inv-form-ticker').value = ticker;
    
    const radio = document.querySelector(`input[name="inv-operacao"][value="${tipoOperacao}"]`);
    if (radio) {
      radio.checked = true;
      radio.dispatchEvent(new Event('change'));
    }

    if ($('inv-form-quantidade') && quantidade > 0) {
      $('inv-form-quantidade').value = quantidade;
    }
    if ($('inv-form-preco') && precoAtual > 0) {
      $('inv-form-preco').value = precoAtual;
    }
  }, 150);
}

// ─── Metas por Tipo de Ativo ───────────────────────────────────────────────

window.carregarMetasPorTipo = async function() {
  try {
    const res = await fetch('/api/investimentos/metas-por-tipo');
    const metas = await res.json();
    for (const [tipo, valor] of Object.entries(metas)) {
      const input = document.getElementById('meta-' + tipo);
      if (input) input.value = parseFloat(valor || 0).toFixed(1);
    }
    window.atualizarTotalMetas();
  } catch (err) {
    console.error('Erro ao carregar metas:', err);
  }
};

window.atualizarTotalMetas = function() {
  const tipos = ['ACAO', 'FII', 'RENDA_FIXA', 'ETF', 'TESOURO_DIRETO', 'CRIPTO'];
  let total = 0;
  for (const tipo of tipos) {
    const input = document.getElementById('meta-' + tipo);
    if (input) total += parseFloat(input.value) || 0;
  }
  const indicator = document.getElementById('meta-total-indicator');
  if (indicator) {
    indicator.textContent = 'Total: ' + total.toFixed(1) + '%';
    indicator.style.color = Math.abs(total - 100) < 0.01 ? '#10b981' : '#ef4444';
  }
};

window.salvarMetasPorTipo = async function() {
  const tipos = ['ACAO', 'FII', 'RENDA_FIXA', 'ETF', 'TESOURO_DIRETO', 'CRIPTO'];
  const metas = {};
  let total = 0;
  for (const tipo of tipos) {
    const input = document.getElementById('meta-' + tipo);
    const val = parseFloat(input?.value) || 0;
    metas[tipo] = val;
    total += val;
  }
  
  if (Math.abs(total - 100) > 0.01) {
    if (typeof showToast === 'function') showToast('O total das metas deve ser exatamente 100% (atual: ' + total.toFixed(1) + '%)', 'error');
    return;
  }
  
  try {
    const res = await fetch('/api/investimentos/metas-por-tipo', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(metas)
    });
    if (!res.ok) throw new Error('Erro ao salvar');
    if (typeof showToast === 'function') showToast('Metas salvas com sucesso!', 'success');
    if (typeof loadInvestimentos === 'function') loadInvestimentos();
  } catch (err) {
    if (typeof showToast === 'function') showToast('Erro ao salvar metas: ' + err.message, 'error');
  }
};

// Carrega metas quando a aba de investimentos for exibida
setTimeout(function() {
  if (document.getElementById('metas-por-tipo-container')) {
    window.carregarMetasPorTipo();
  }
}, 1500);

window.switchDistChartMode = function(mode) {
  state.distChartMode = mode;
  const btnTipo = document.getElementById('btn-dist-tipo');
  const btnTatica = document.getElementById('btn-dist-tatica');
  if (btnTipo) btnTipo.classList.toggle('active', mode === 'tipo');
  if (btnTatica) btnTatica.classList.toggle('active', mode === 'tatica');
  if (state.lastDashboardData) {
    renderInvDistChart(state.lastDashboardData);
  }
};
