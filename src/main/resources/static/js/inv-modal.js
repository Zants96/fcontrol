/**
 * inv-modal.js – Modais de lançamento, edição e preço de ativos + BRAPI/CoinGecko config
 * Depende de: inv-constants.js
 */

// ─── UTILS DE FORMULÁRIO ─────────────────────────────────────────────────

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
        return `${y}-${parts[1].padStart(2, '0')}-${parts[0].padStart(2, '0')}`;
      }
    }
  }
  return null;
}

function formatIsoToBrDate(isoStr) {
  if (!isoStr) return '';
  const parts = isoStr.split('-');
  if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
  return isoStr;
}

function applyPercentMask(input) {
  if (!input) return;
  input.addEventListener('focus', (e) => {
    e.target.value = e.target.value.replace('%', '').trim();
  });
  input.addEventListener('blur', (e) => {
    const val = e.target.value.replace('%', '').trim();
    if (val !== '') e.target.value = val + '%';
  });
}

function parseInvInput(val) {
  if (!val) return 0;
  if (typeof val === 'string') val = val.replace('%', '').trim();
  if (val.includes(',')) return parseFloat(val.replace(/\./g, '').replace(',', '.'));
  return parseFloat(val);
}

function calcInvTotal() {
  const operacao = document.querySelector('input[name="inv-operacao"]:checked')?.value;
  const preco = parseInvInput($('inv-form-preco')?.value);
  const custos = parseInvInput($('inv-form-custos')?.value);
  const qtd = parseInvInput($('inv-form-quantidade')?.value);

  let total;
  if (operacao === 'DIVIDENDO') {
    total = qtd > 0 ? (qtd * preco) + custos : preco + custos;
  } else {
    total = (qtd * preco) + custos;
  }

  $('inv-form-total').textContent = fmtCurrency(total);

  if (operacao === 'DIVIDENDO') {
    const tipoProv = $('inv-form-tipo-provento')?.value;
    const liqInput = $('inv-form-liquido');
    if (liqInput && tipoProv === 'JSCP' && (!liqInput.value || liqInput._autoCalculated)) {
      liqInput.value = fmtCurrency(total * 0.85).replace('R$', '').trim();
      liqInput._autoCalculated = true;
    }
  }
}

// ─── MODAL DE LANÇAMENTO ─────────────────────────────────────────────────

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

  document.querySelectorAll('input[name="inv-operacao"]').forEach(r => {
    r.addEventListener('change', () => {
      const isDividendo = r.value === 'DIVIDENDO';
      $('inv-group-quantidade').style.display = '';
      const groupLiquido = $('inv-group-liquido');
      if (groupLiquido) groupLiquido.style.display = isDividendo ? '' : 'none';
      const groupTipoProvento = $('inv-group-tipo-provento');
      if (groupTipoProvento) groupTipoProvento.style.display = isDividendo ? '' : 'none';
      $('inv-label-preco').textContent = isDividendo ? 'Valor por cota / Bruto (R$)' : 'Preço unitário (R$)';
      $('inv-form-preco').placeholder = isDividendo ? 'Ex: 0,09 ou 2,74' : '0,00';
      $('inv-form-quantidade').placeholder = isDividendo ? 'Ex: 29 (opcional)' : 'Ex: 100';
    });
  });

  $('inv-form-liquido')?.addEventListener('input', (e) => {
    e.target._autoCalculated = false;
    let val = e.target.value.replace(/\D/g, '');
    if (val === '') return;
    val = (parseInt(val, 10) / 100).toFixed(2);
    val = val.replace('.', ',');
    val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.');
    e.target.value = val;
  });

  $('inv-form-tipo-provento')?.addEventListener('change', calcInvTotal);

  const formatCurrency = (e) => {
    let val = e.target.value.replace(/\D/g, '');
    if (val === '') return;
    val = (parseInt(val, 10) / 100).toFixed(2);
    val = val.replace('.', ',');
    val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.');
    e.target.value = val;
    calcInvTotal();
  };

  $('inv-form-preco')?.addEventListener('input', formatCurrency);
  $('inv-form-custos')?.addEventListener('input', formatCurrency);
  $('inv-form-quantidade')?.addEventListener('input', calcInvTotal);

  $('inv-form-ticker')?.addEventListener('input', (e) => {
    const val = e.target.value.trim().toUpperCase();
    if (val === 'ABTC11') {
      const tipoSelect = $('inv-form-tipo');
      if (tipoSelect && tipoSelect.value !== 'CRIPTO') {
        tipoSelect.value = 'CRIPTO';
        tipoSelect.dispatchEvent(new Event('change'));
      }
    }
  });

  const inputAporte = $('input-aporte-valor');
  if (inputAporte && !inputAporte.dataset.maskBound) {
    inputAporte.dataset.maskBound = 'true';
    inputAporte.addEventListener('input', (e) => {
      let val = e.target.value.replace(/\D/g, '');
      if (val === '') { e.target.value = ''; return; }
      val = (parseInt(val, 10) / 100).toFixed(2);
      val = val.replace('.', ',');
      val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.');
      e.target.value = val;
    });
  }

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
    const catSelect = $('inv-form-categoria-tatica');
    if (catSelect && !catSelect._manuallySelected) {
      if (tipo === 'RENDA_FIXA' || tipo === 'TESOURO_DIRETO') catSelect.value = 'SEGURANCA';
      else if (tipo === 'ACAO') catSelect.value = 'CRESCIMENTO';
      else if (tipo === 'FII') catSelect.value = 'RENDA';
      else if (tipo === 'ETF') catSelect.value = 'GLOBAL';
      else if (tipo === 'CRIPTO') catSelect.value = 'CRESCIMENTO';
    }
  };
  $('inv-form-tipo')?.addEventListener('change', updateRendaFixaFields);
  $('inv-form-categoria-tatica')?.addEventListener('change', () => {
    const catSelect = $('inv-form-categoria-tatica');
    if (catSelect) catSelect._manuallySelected = true;
  });
}

function openInvModal(tipoAtivo) {
  $('inv-form').reset();
  const catSelect = $('inv-form-categoria-tatica');
  if (catSelect) catSelect._manuallySelected = false;
  if (tipoAtivo) $('inv-form-tipo').value = tipoAtivo;

  $('inv-form-tipo')?.dispatchEvent(new Event('change'));

  const compraRadio = document.querySelector('input[name="inv-operacao"][value="COMPRA"]');
  if (compraRadio) compraRadio.checked = true;
  $('inv-group-quantidade').style.display = '';
  $('inv-label-preco').textContent = 'Preço unitário (R$)';
  $('inv-form-preco').placeholder = '0,00';
  $('inv-form-quantidade').placeholder = 'Ex: 100';
  const groupLiquido = $('inv-group-liquido');
  if (groupLiquido) groupLiquido.style.display = 'none';
  const groupTipoProvento = $('inv-group-tipo-provento');
  if (groupTipoProvento) {
    groupTipoProvento.style.display = 'none';
    $('inv-form-tipo-provento').value = 'Dividendo';
  }

  const today = new Date();
  const todayD = String(today.getDate()).padStart(2, '0');
  const todayM = String(today.getMonth() + 1).padStart(2, '0');
  $('inv-form-data').value = `${todayD}/${todayM}/${state.ano}`;
  $('inv-form-total').textContent = 'R$ 0,00';

  $('inv-modal-overlay').classList.remove('hidden');
  $('main-content').setAttribute('aria-hidden', 'true');
  setTimeout(() => $('inv-form-ticker')?.focus(), 100);
}

function closeInvModal() {
  $('inv-modal-overlay').classList.add('hidden');
  $('main-content').removeAttribute('aria-hidden');
}

async function onInvFormSubmit(e) {
  e.preventDefault();

  const operacao = document.querySelector('input[name="inv-operacao"]:checked')?.value;
  const ticker = $('inv-form-ticker')?.value?.trim().toUpperCase();
  let tipoAtivo = $('inv-form-tipo')?.value;
  if (ticker === 'ABTC11') tipoAtivo = 'CRIPTO';
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
  if (!data || !/^\d{4}-\d{2}-\d{2}$/.test(data)) { showToast('Informe uma data de operação válida (DD/MM/YYYY).', 'error'); return; }
  if (tipoAtivo === 'RENDA_FIXA' || tipoAtivo === 'TESOURO_DIRETO') {
    if (!dataVencimento || !/^\d{4}-\d{2}-\d{2}$/.test(dataVencimento)) { showToast('Informe uma data de vencimento válida (DD/MM/YYYY).', 'error'); return; }
  }
  if (preco <= 0) { showToast('Informe um preço válido.', 'error'); return; }
  if (operacao !== 'DIVIDENDO' && quantidade <= 0) { showToast('Informe uma quantidade válida.', 'error'); return; }

  const liquidoRaw = $('inv-form-liquido')?.value;
  const valorLiquido = (operacao === 'DIVIDENDO' && liquidoRaw) ? parseInvInput(liquidoRaw) : null;
  const tipoProvento = (operacao === 'DIVIDENDO') ? $('inv-form-tipo-provento')?.value || 'Dividendo' : null;
  let valorTotal = null;
  if (operacao === 'DIVIDENDO') {
    valorTotal = quantidade > 0 ? (quantidade * preco) + custos : preco + custos;
  }

  const dto = {
    ticker, tipoAtivo, tipoOperacao: operacao,
    data, quantidade: operacao === 'DIVIDENDO' && quantidade <= 0 ? 0 : quantidade,
    precoUnitario: preco, custos, valorLiquido, valorTotal,
    dataVencimento, indexador, taxa, tipoProvento
  };

  const btn = $('inv-btn-save');
  btn.textContent = 'Salvando...';
  btn.disabled = true;

  try {
    await Api.criarLancamentoInvestimento(dto);
    showToast('Lançamento registrado!', 'success');
    if (confirm('Lançamento registrado com sucesso!\n\nDeseja registrar outro lançamento de investimento agora?')) {
      openInvModal(tipoAtivo);
      loadInvestimentos();
      return;
    } else {
      closeInvModal();
      loadInvestimentos();
    }
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

    $('inv-preco-form').addEventListener('submit', async (e) => {
      e.preventDefault();
      const idStr = $('inv-preco-id').value;
      const tck = $('inv-preco-ticker').value;
      const preco = parseInvInput($('inv-preco-valor').value);
      if (isNaN(preco) || preco < 0) { showToast('Preço inválido.', 'error'); return; }
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

  $('inv-preco-id').value = id;
  $('inv-preco-ticker').value = ticker;
  $('inv-preco-modal-desc').innerHTML = `Preço atual de <strong>${ticker}</strong>: R$ ${parseFloat(precoAtual).toFixed(2).replace('.', ',')}`;
  $('inv-preco-valor').value = fmtCurrency(precoAtual).replace('R$', '').trim();
  $('inv-preco-modal-overlay').classList.remove('hidden');
  $('main-content').setAttribute('aria-hidden', 'true');
}

// ─── MODAL DE EDIÇÃO DE LANÇAMENTO ───────────────────────────────────────

function closeInvEditModal() {
  $('inv-edit-modal-overlay').classList.add('hidden');
  $('main-content').removeAttribute('aria-hidden');
}

function initInvEditModalListeners() {
  if (!$('inv-edit-modal-overlay')) {
    const modalHtml = `
      <div id="inv-edit-modal-overlay" class="modal-overlay hidden" style="z-index: 9999;">
        <div class="modal inv-modal">
          <div class="modal-header">
            <h2>Editar Lançamento</h2>
            <button class="modal-close" id="inv-edit-modal-close" aria-label="Fechar janela">✕</button>
          </div>
          <form id="inv-edit-form" class="modal-body">
            <input type="hidden" id="inv-edit-id" />
            <input type="hidden" id="inv-edit-ativo-id" />
            <input type="hidden" id="inv-edit-op" />
            <div class="form-group">
              <label for="inv-edit-categoria-tatica">Categoria Tática</label>
              <select id="inv-edit-categoria-tatica" class="form-input">
                <option value="SEGURANCA">Segurança &amp; Liquidez</option>
                <option value="RENDA">Renda Passiva</option>
                <option value="CRESCIMENTO">Crescimento / Capital</option>
                <option value="GLOBAL">Global / Internacional</option>
                <option value="PREVIDENCIA">Previdência</option>
              </select>
            </div>
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
            <div class="form-group" id="inv-edit-group-liquido" style="display:none;">
              <label for="inv-edit-liquido">Total Líquido (R$)</label>
              <input type="text" id="inv-edit-liquido" class="form-input" placeholder="0,00" />
            </div>
            <div class="form-group" id="inv-edit-group-tipo-provento" style="display:none;">
              <label for="inv-edit-tipo-provento">Tipo de Provento</label>
              <select id="inv-edit-tipo-provento" class="form-input">
                <option value="Dividendo" selected>Dividendo</option>
                <option value="JSCP">JSCP</option>
                <option value="Rend. Trib.">Rend. Trib.</option>
              </select>
            </div>
            <div class="form-group">
              <label for="inv-edit-data">Data</label>
              <input type="text" id="inv-edit-data" class="form-input" placeholder="DD/MM/YYYY" maxlength="10" required />
            </div>
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
  applyDateMask($('inv-edit-data'));
  applyPercentMask($('inv-edit-taxa'));

  $('inv-edit-modal-close')?.addEventListener('click', closeInvEditModal);
  $('inv-edit-btn-cancel')?.addEventListener('click', closeInvEditModal);
  $('inv-edit-modal-overlay')?.addEventListener('click', e => {
    if (e.target === $('inv-edit-modal-overlay')) closeInvEditModal();
  });

  const calcEditTotal = () => {
    const op = $('inv-edit-op').value;
    if (op !== 'DIVIDENDO') return;
    const preco = parseInvInput($('inv-edit-preco')?.value);
    const custos = parseInvInput($('inv-edit-custos')?.value);
    const qtd = parseInvInput($('inv-edit-qtd')?.value);
    const tipoProv = $('inv-edit-tipo-provento')?.value;
    const total = (qtd > 0) ? (qtd * preco) + custos : preco + custos;
    const liqInput = $('inv-edit-liquido');
    if (liqInput && tipoProv === 'JSCP' && (!liqInput.value || liqInput._autoCalculated)) {
      liqInput.value = fmtCurrency(total * 0.85).replace('R$', '').trim();
      liqInput._autoCalculated = true;
    }
  };

  const formatCurrency = (e) => {
    let val = e.target.value.replace(/\D/g, '');
    if (val === '') return;
    val = (parseInt(val, 10) / 100).toFixed(2);
    val = val.replace('.', ',');
    val = val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.');
    e.target.value = val;
  };

  $('inv-edit-preco')?.addEventListener('input', (e) => { formatCurrency(e); calcEditTotal(); });
  $('inv-edit-custos')?.addEventListener('input', (e) => { formatCurrency(e); calcEditTotal(); });
  $('inv-edit-liquido')?.addEventListener('input', (e) => { formatCurrency(e); e.target._autoCalculated = false; });
  $('inv-edit-qtd')?.addEventListener('input', calcEditTotal);
  $('inv-edit-tipo-provento')?.addEventListener('change', calcEditTotal);

  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = $('inv-edit-id').value;
    const ativoId = $('inv-edit-ativo-id').value;
    const op = $('inv-edit-op').value;

    let novaQtd = 0;
    const qtdRaw = $('inv-edit-qtd').value;
    if (op !== 'DIVIDENDO') {
      novaQtd = parseInvInput(qtdRaw);
      if (isNaN(novaQtd) || novaQtd < 0) { showToast('Quantidade inválida.', 'error'); return; }
    } else {
      novaQtd = qtdRaw ? parseInvInput(qtdRaw) : 0;
      if (isNaN(novaQtd) || novaQtd < 0) novaQtd = 0;
    }

    const novoPreco = parseInvInput($('inv-edit-preco').value);
    const novosCustos = parseInvInput($('inv-edit-custos').value);
    if (isNaN(novoPreco) || novoPreco < 0) { showToast('Preço inválido.', 'error'); return; }

    const dataRaw = $('inv-edit-data')?.value;
    const data = convertDateToIso(dataRaw);
    if (!data || !/^\d{4}-\d{2}-\d{2}$/.test(data)) { showToast('Informe uma data de operação válida (DD/MM/YYYY).', 'error'); return; }

    const isRF = $('inv-edit-group-renda-fixa') && !$('inv-edit-group-renda-fixa').classList.contains('hidden');
    const dataVencimentoRaw = isRF ? ($('inv-edit-vencimento')?.value || null) : null;
    const dataVencimento = convertDateToIso(dataVencimentoRaw);
    if (isRF && (!dataVencimento || !/^\d{4}-\d{2}-\d{2}$/.test(dataVencimento))) {
      showToast('Informe uma data de vencimento válida (DD/MM/YYYY).', 'error'); return;
    }

    const indexador = isRF ? ($('inv-edit-indexador')?.value || null) : null;
    const taxaRaw = isRF ? ($('inv-edit-taxa')?.value || null) : null;
    const taxa = taxaRaw ? parseInvInput(taxaRaw) : null;

    try {
      const valorTotal = (op === 'DIVIDENDO' && novaQtd <= 0) ? (novoPreco + novosCustos) : ((novaQtd * novoPreco) + novosCustos);
      const liquidoRaw = op === 'DIVIDENDO' ? $('inv-edit-liquido')?.value : null;
      const valorLiquido = liquidoRaw ? parseInvInput(liquidoRaw) : null;
      const tipoProvento = op === 'DIVIDENDO' ? $('inv-edit-tipo-provento')?.value || 'Dividendo' : null;
      await Api.updateInvestimentoLancamento(id, {
        ticker: form._currentTicker,
        tipoAtivo: form._currentTipoAtivo,
        tipoOperacao: op,
        quantidade: novaQtd,
        precoUnitario: novoPreco,
        custos: novosCustos,
        valorTotal, valorLiquido, data, dataVencimento, indexador, taxa, tipoProvento
      });
      showToast('Lançamento atualizado!', 'success');
      closeInvEditModal();
      const lancamentos = await Api.getLancamentosInvestimento(ativoId);
      renderizarTabelaHistorico(lancamentos);
      loadInvestimentos();
      form.dispatchEvent(new CustomEvent('inv-saved'));
    } catch (err) {
      showToast('Erro ao atualizar lançamento: ' + err.message, 'error');
    }
  });
}

async function editarLancamentoInvestimento(id) {
  const l = window._currentLancamentos?.find(x => x.id === id);
  if (!l) return;

  $('inv-edit-id').value = l.id;
  $('inv-edit-ativo-id').value = l.ativoId;
  $('inv-edit-op').value = l.tipoOperacao;
  const form = $('inv-edit-form');
  if (form) {
    form._currentTicker = l.ticker;
    form._currentTipoAtivo = (l.ticker && l.ticker.toUpperCase() === 'ABTC11') ? 'CRIPTO' : l.tipoAtivo;
  }

  const formQtd = $('inv-edit-qtd');
  const formPreco = $('inv-edit-preco');
  const formCustos = $('inv-edit-custos');
  const groupQtd = $('inv-edit-group-qtd');
  const labelPreco = $('inv-edit-label-preco');

  if (l.tipoOperacao === 'DIVIDENDO') {
    groupQtd.style.display = '';
    formQtd.value = l.quantidade != null && parseFloat(l.quantidade) > 0 ? formatQtd(l.quantidade) : '';
    labelPreco.textContent = 'Valor por cota / Bruto (R$)';
    const groupLiquido = $('inv-edit-group-liquido');
    if (groupLiquido) {
      groupLiquido.style.display = '';
      const liquidoVal = l.valorLiquido != null ? l.valorLiquido : l.valorTotal;
      $('inv-edit-liquido').value = fmtCurrency(liquidoVal || 0).replace('R$', '').trim();
      if (l.tipoProvento === 'JSCP') $('inv-edit-liquido')._autoCalculated = true;
    }
    const groupTipoProvento = $('inv-edit-group-tipo-provento');
    if (groupTipoProvento) {
      groupTipoProvento.style.display = '';
      $('inv-edit-tipo-provento').value = l.tipoProvento || 'Dividendo';
    }
  } else {
    groupQtd.style.display = '';
    formQtd.value = formatQtd(l.quantidade);
    labelPreco.textContent = 'Preço unitário (R$)';
    const groupLiquido = $('inv-edit-group-liquido');
    if (groupLiquido) groupLiquido.style.display = 'none';
    const groupTipoProvento = $('inv-edit-group-tipo-provento');
    if (groupTipoProvento) groupTipoProvento.style.display = 'none';
  }

  formPreco.value = fmtCurrency(l.precoUnitario || 0).replace('R$', '').trim();
  formCustos.value = fmtCurrency(l.custos || 0).replace('R$', '').trim();
  $('inv-edit-data').value = formatIsoToBrDate(l.data);

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

  $('inv-edit-modal-overlay').classList.remove('hidden');
  $('main-content').setAttribute('aria-hidden', 'true');
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

async function checkCoingeckoStatus() {
  try {
    const status = await Api.getCoingeckoStatus();
    const el = $('coingecko-key-status');
    if (el) {
      el.innerHTML = status.configurado
        ? '<span style="color:var(--brand-glow);">✅ Chave CoinGecko configurada</span>'
        : '<span style="color:var(--text-muted);">ℹ️ Usando chave de demonstração padrão</span>';
    }
  } catch (e) { /* silently fail */ }
}

function initCoingeckoConfig() {
  $('btn-save-coingecko-key')?.addEventListener('click', async () => {
    const key = $('input-coingecko-key')?.value?.trim();
    if (!key) { showToast('Cole a chave de API do CoinGecko.', 'error'); return; }
    try {
      await Api.saveCoingeckoKey(key);
      showToast('Chave CoinGecko salva!', 'success');
      $('input-coingecko-key').value = '';
      checkCoingeckoStatus();
    } catch (err) {
      showToast(err.message, 'error');
    }
  });
}
