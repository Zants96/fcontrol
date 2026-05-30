/**
 * app.js – Lógica principal da SPA MyTwoCents
 */

// ─── Subcategorias por Categoria ─────────────────────────────────────────────
const SUBCATEGORIAS = {
  RECEITA: [
    '13º Salário', 'Férias', 'Freelancer', 'Outras Receitas', 'Participação nos Lucros', 
    'Proventos', 'Resgate de Investimentos', 'Restituição de IR', 'Salário', 'Vendas'
  ],
  GASTO: [
    'Água', 'Alimentação', 'Aluguel', 'Cartão de Crédito', 'Consultas', 
    'Educação', 'Empréstimo', 'Investimentos', 'Lanches', 'Lazer', 'Manutenção/Reparos', 
    'Medicamentos', 'Outros', 'Pets', 'Presentes / Doações', 'Prestações', 
    'Restaurante', 'Saúde & Beleza', 'Taxas/Impostos', 'Transporte', 'Vestuário', 'Viagens'
  ],
  GASTO_FIXO: [
    'Água', 'Aluguel', 'Condomínio', 'Energia/Luz', 'Impostos', 
    'Internet', 'Investimentos', 'Outros', 'Prestação', 'Seguro', 'Seguro Residencial', 
    'Telefonia'
  ],
  ASSINATURA: [
    'Educação/Cursos', 'Jogos/Consoles', 'Leitura/Notícias', 'Outros', 'Serviços de Assinatura', 
    'Serviços Digitais/Cloud', 'Streaming de Áudio', 'Streaming de Vídeo'
  ],
};

// ─── Estado Global ───────────────────────────────────────────────────────────
const state = {
  view:        'dashboard',
  ano:         new Date().getFullYear(),
  mes:         new Date().getMonth() + 1,
  categoria:   null,
  lancamentos: [],
  editingId:   null,
};

const MESES_FULL = [
  'Janeiro','Fevereiro','Março','Abril','Maio','Junho',
  'Julho','Agosto','Setembro','Outubro','Novembro','Dezembro'
];

const CATEGORIA_LABEL = {
  RECEITA:    'Receitas',
  GASTO:      'Gastos',
  GASTO_FIXO: 'Gastos Fixos',
  ASSINATURA: 'Assinaturas',
};

const CATEGORIA_VIEW = {
  receitas:       'RECEITA',
  gastos:         'GASTO',
  'gastos-fixos': 'GASTO_FIXO',
  assinaturas:    'ASSINATURA',
};

// ─── DOM Refs ─────────────────────────────────────────────────────────────────
const $ = id => document.getElementById(id);
const $$ = sel => document.querySelectorAll(sel);

// ─── Inicialização ────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  initYearSelector();
  initDate();
  initNavigation();
  initModal();
  initInvModal();
  initMenuToggle();
  initExport();
  initBackup();
  initMonthlyDashboard();
  initTheme();
  initAiChat();
  initBrapiConfig();
  navigateTo('dashboard');
});

// ─── Tema (Light/Dark) ────────────────────────────────────────────────────────
function initTheme() {
  const saved = localStorage.getItem('mytwocents-theme') || 'dark';
  document.documentElement.setAttribute('data-theme', saved);
  
  const setTheme = (t) => {
    if (saved === t) return; // evita reload desnecessário se já estiver no tema
    document.documentElement.setAttribute('data-theme', t);
    localStorage.setItem('mytwocents-theme', t);
    setTimeout(() => location.reload(), 50);
  };

  const btnLight = $('btn-theme-light');
  const btnDark = $('btn-theme-dark');
  
  if (btnLight) btnLight.addEventListener('click', () => setTheme('light'));
  if (btnDark) btnDark.addEventListener('click', () => setTheme('dark'));
}

// ─── Ano e Data ───────────────────────────────────────────────────────────────
function initYearSelector() {
  const sel = $('select-ano');
  const current = new Date().getFullYear();
  for (let y = current + 1; y >= current - 4; y--) {
    const opt = document.createElement('option');
    opt.value = y;
    opt.textContent = y;
    if (y === current) opt.selected = true;
    sel.appendChild(opt);
  }
  sel.addEventListener('change', () => {
    state.ano = parseInt(sel.value);
    refreshView();
  });
}

function initDate() {
  const d = new Date();
  $('current-date').textContent = d.toLocaleDateString('pt-BR', {
    weekday: 'short', day: '2-digit', month: 'long', year: 'numeric'
  });
}

// ─── Navegação ────────────────────────────────────────────────────────────────
function initNavigation() {
  $$('.nav-item').forEach(item => {
    item.addEventListener('click', e => {
      e.preventDefault();
      navigateTo(item.dataset.view);
      $('sidebar').classList.remove('open');
    });
  });
}

function navigateTo(view) {
  state.view = view;

  $$('.nav-item').forEach(el => el.classList.remove('active'));
  const navItem = $(`nav-${view}`);
  if (navItem) navItem.classList.add('active');

  const titles = {
    dashboard:       'Dashboard',
    receitas:        'Receitas',
    gastos:          'Gastos',
    'gastos-fixos':  'Gastos Fixos',
    assinaturas:     'Assinaturas',
    investimentos:   'Investimentos',
    'assistente-ia': 'Assistente IA',
    configuracoes:   'Configurações',
  };
  $('page-title').textContent = titles[view] || view;

  $('view-dashboard').classList.add('hidden');
  $('view-tabela').classList.add('hidden');
  $('view-investimentos')?.classList.add('hidden');
  $('view-assistente-ia')?.classList.add('hidden');
  $('view-configuracoes')?.classList.add('hidden');

  if (view === 'dashboard') {
    $('view-dashboard').classList.remove('hidden');
    loadDashboard();
  } else if (view === 'investimentos') {
    $('view-investimentos').classList.remove('hidden');
    loadInvestimentos();
  } else if (view === 'assistente-ia') {
    $('view-assistente-ia').classList.remove('hidden');
    loadAiInsights('ai-insights-chat');
  } else if (view === 'configuracoes') {
    $('view-configuracoes')?.classList.remove('hidden');
    checkAiKeyStatus();
    checkBrapiStatus();
  } else {
    state.categoria = CATEGORIA_VIEW[view];
    
    const labelTipo = CATEGORIA_LABEL[state.categoria] || 'Gastos';
    const tDist = $('title-dist-tabela');
    const tTop = $('title-top-tabela');
    const tTotal = $('title-total-tabela');
    if (tDist) tDist.textContent = `Distribuição de ${labelTipo} do Mês`;
    if (tTop) tTop.textContent = `Top 5 ${labelTipo} do Mês`;
    if (tTotal) {
      if (state.categoria === 'RECEITA') {
        tTotal.textContent = 'Total Recebido no Mês';
      } else if (state.categoria === 'GASTO') {
        tTotal.textContent = 'Total Gasto no Mês';
      } else if (state.categoria === 'GASTO_FIXO') {
        tTotal.textContent = 'Total Gasto Fixo no Mês';
      } else if (state.categoria === 'ASSINATURA') {
        tTotal.textContent = 'Total de Assinaturas no Mês';
      } else {
        tTotal.textContent = `Total de ${labelTipo} no Mês`;
      }
    }

    $('view-tabela').classList.remove('hidden');
    buildMonthTabs();
    loadTabela();
  }
}

function refreshView() {
  navigateTo(state.view);
}

// ─── Exportação ──────────────────────────────────────────────────────────────
function buildExportUrl(format, isFullReport) {
  const mes = isFullReport ? 0 : state.mes;
  const view = isFullReport ? 'dashboard' : state.view;
  const safeMes = Number.isInteger(mes) && mes > 0 && mes <= 12 ? mes : null;
  return `/api/lancamentos/export/${format}?ano=${state.ano}&` + (safeMes !== null ? `mes=${safeMes}&` : '') + `view=${view}`;
}

function buildFileName(format, isFullReport) {
  const MESES_NOME = [
    'Janeiro','Fevereiro','Março','Abril','Maio','Junho',
    'Julho','Agosto','Setembro','Outubro','Novembro','Dezembro'
  ];
  const VIEW_NOMES = {
    'receitas': 'Receitas',
    'gastos': 'Gastos',
    'gastos-fixos': 'Gastos Fixos',
    'assinaturas': 'Assinaturas',
    'dashboard': 'Geral'
  };
  let name = `MyTwoCents - ${state.ano}`;
  const mes = isFullReport ? 0 : state.mes;
  const view = isFullReport ? 'dashboard' : state.view;
  
  if (mes > 0) {
    name += ` - ${MESES_NOME[mes - 1]}`;
  }
  name += ` - ${VIEW_NOMES[view] || 'Geral'}`;
  return `${name}.${format}`;
}

function triggerExport(format, isFullReport = false) {
  const url = buildExportUrl(format, isFullReport);
  const fileName = buildFileName(format, isFullReport);

  // Ambiente JavaFX Desktop: usa FileChooser nativo do sistema
  if (window.javaBridge) {
    window.javaBridge.saveFile(url, fileName, null);
    return;
  }

  // Fallback para navegador comum
  fetch(url)
    .then(res => {
      if (!res.ok) throw new Error('Erro ao baixar arquivo');
      return res.blob();
    })
    .then(blob => {
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = fileName;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(link.href);
      showToast('Download concluído!');
    })
    .catch(err => showToast('Falha no download: ' + err.message, 'error'));
}

function initExport() {
  $('btn-export-csv').addEventListener('click', () => triggerExport('csv', true));
  $('btn-export-pdf').addEventListener('click', () => triggerExport('pdf', true));
  
  const catCsv = $('btn-export-cat-csv');
  if (catCsv) catCsv.addEventListener('click', () => triggerExport('csv', false));
  const catPdf = $('btn-export-cat-pdf');
  if (catPdf) catPdf.addEventListener('click', () => triggerExport('pdf', false));
}

// ─── Mobile Menu ─────────────────────────────────────────────────────────────
function initMenuToggle() {
  const btn = $('menu-toggle');
  btn.addEventListener('click', () => {
    $('sidebar').classList.toggle('open');
    const isOpen = $('sidebar').classList.contains('open');
    btn.setAttribute('aria-expanded', isOpen);
  });
}

// ─── DASHBOARD ────────────────────────────────────────────────────────────────
async function loadDashboard() {
  setDashboardLoadingState();
  try {
    const data = await Api.getDashboard(state.ano);
    renderDashboardCards(data);
    renderBarChart(data);
    renderDonutChart(data);
    renderLineChart(data);
    renderTopGastos(data.topGastos);
    
    // Dashboard Mensal
    const mesSel = document.getElementById('select-dash-mes');
    const parsedMes = mesSel ? parseInt(mesSel.value) : NaN;
    const mesSelecionado = !isNaN(parsedMes) ? parsedMes : (new Date().getMonth() + 1);
    renderMonthlyDashboard(data, mesSelecionado);

    // Insights da IA (com cache de 4h)
    loadAiInsights('ai-insights-dashboard');
  } catch (err) {
    showToast('Erro ao carregar dashboard: ' + err.message, 'error');
    console.error(err);
  }
}

function setDashboardLoadingState() {
  ['card-receitas','card-gastos','card-assinaturas','card-saldo'].forEach(id => {
    const el = $(id);
    el.textContent = 'carregando...';
    el.style.animation = 'pulse 1.2s infinite';
  });
}

function renderDashboardCards(data) {
  const fields = [
    ['card-receitas',    data.totalReceitas,    'card-receitas-sub',    'Total anual de receitas'],
    ['card-gastos',      data.totalGastos,       'card-gastos-sub',      'Gastos + Gastos Fixos no ano'],
    ['card-assinaturas', data.totalAssinaturas,  'card-assinaturas-sub', 'Total anual de assinaturas'],
    ['card-saldo',       data.saldoAnual,        'card-saldo-sub',       'Receitas − Gastos − Assinaturas'],
  ];

  fields.forEach(([id, val, subId, label]) => {
    const el = $(id);
    el.style.animation = '';
    el.textContent = fmtCurrency(parseFloat(val));
    if (subId) $(subId).textContent = label;
  });
}

// ─── TABELA ───────────────────────────────────────────────────────────────────
function buildMonthTabs() {
  const container = $('month-tabs');
  container.innerHTML = MESES_FULL.map((m, i) => `
    <button class="month-tab ${i + 1 === state.mes ? 'active' : ''}"
            data-mes="${i + 1}" id="tab-mes-${i + 1}">
      ${m.substring(0,3)}
    </button>
  `).join('');

  container.querySelectorAll('.month-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      const parsed = parseInt(tab.dataset.mes);
      const valid = Number.isInteger(parsed) && parsed >= 1 && parsed <= 12;
      state.mes = valid ? parsed : (new Date().getMonth() + 1);
      // update active class correctly
      container.querySelectorAll('.month-tab').forEach(t => t.classList.remove('active'));
      const activeTab = container.querySelector(`.month-tab[data-mes='${state.mes}']`);
      if (activeTab) activeTab.classList.add('active');
      loadTabela();
    });
  });
}

async function loadTabela() {
  const tbody = $('table-body');
  tbody.innerHTML = `<tr><td colspan="5"><div class="loading-overlay"><div class="spinner"></div> Carregando...</div></td></tr>`;
  $('table-total').innerHTML = '<strong>Calculando...</strong>';

  try {
    const lancamentos = await Api.getLancamentos({
      ano: state.ano,
      mes: state.mes,
      categoria: state.categoria,
    });

    state.lancamentos = lancamentos;
    renderTabela(lancamentos);

    if (!_dashboardData || _dashboardData.ano !== state.ano) {
      _dashboardData = await Api.getDashboard(state.ano);
    }
    renderMonthlyDashboard(_dashboardData, state.mes, 't-');
    
    // Insights focados por categoria e mês selecionado
    if (typeof loadAiInsightsTabela === 'function') {
      loadAiInsightsTabela();
    }
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="5"><div class="empty-state"><div class="empty-icon"><svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/></svg></div><p>${err.message}</p></div></td></tr>`;
    showToast(err.message, 'error');
  }
}

function renderTabela(lancamentos) {
  const tbody = $('table-body');

  if (lancamentos.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="5">
          <div class="empty-state">
            <div class="empty-icon"><svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="22 12 16 12 14 15 10 15 8 12 2 12"/><path d="M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"/></svg></div>
            <p>Nenhum lançamento para este mês.<br>Clique em <strong>+ Adicionar</strong> para incluir.</p>
          </div>
        </td>
      </tr>`;
    $('table-total').innerHTML = '<strong>R$ 0,00</strong>';
    return;
  }

  const total = lancamentos.reduce((acc, l) => acc + parseFloat(l.valor || 0), 0);
  $('table-total').innerHTML = `<strong>${fmtCurrency(total)}</strong>`;

  tbody.innerHTML = lancamentos.map(l => {
    const valor = parseFloat(l.valor || 0);
    return `
      <tr id="row-${l.id}">
        <td><span class="subcategoria-badge">${escHtml(l.subcategoria)}</span></td>
        <td>${escHtml(l.descricao || l.subcategoria)}</td>
        <td class="cell-valor">${fmtCurrency(valor)}</td>
        <td>${l.dia || '-'}</td>
        <td class="col-actions">
          <div class="action-btns">
            <button class="action-btn action-btn--edit" title="Editar" onclick="openEditModal(${l.id})"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/><path d="m15 5 4 4"/></svg></button>
            <button class="action-btn action-btn--del"  title="Excluir" onclick="excluir(${l.id})"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18"/><path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/><path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/><line x1="10" x2="10" y1="11" y2="17"/><line x1="14" x2="14" y1="11" y2="17"/></svg></button>
          </div>
        </td>
      </tr>
    `;
  }).join('');
}

// ─── SUBCATEGORIA SELECT DINÂMICO ─────────────────────────────────────────────

/**
 * Popula o select de subcategoria baseado na categoria selecionada.
 * Se currentValue for passado e não estiver na lista, adiciona como opção extra.
 */
function populateSubcategoriaSelect(categoria, currentValue) {
  const sel = $('form-subcategoria');
  const options = SUBCATEGORIAS[categoria] || [];

  sel.innerHTML = '';

  // Placeholder
  const placeholder = document.createElement('option');
  placeholder.value = '';
  placeholder.textContent = '— Selecione —';
  placeholder.disabled = true;
  placeholder.selected = !currentValue;
  sel.appendChild(placeholder);

  // Opcões da categoria
  options.forEach(opt => {
    const el = document.createElement('option');
    el.value = opt;
    el.textContent = opt;
    if (opt === currentValue) el.selected = true;
    sel.appendChild(el);
  });

  // Se o valor atual não estiver na lista (dado legado ou customizado), adiciona
  if (currentValue && !options.includes(currentValue)) {
    const el = document.createElement('option');
    el.value = currentValue;
    el.textContent = currentValue + ' (personalizado)';
    el.selected = true;
    sel.appendChild(el);
  }
}

// ─── MODAL ────────────────────────────────────────────────────────────────────
function initModal() {
  $('btn-add-row').addEventListener('click', openCreateModal);
  $('modal-close').addEventListener('click', closeModal);
  $('btn-cancel').addEventListener('click',  closeModal);
  $('modal-overlay').addEventListener('click', e => {
    if (e.target === $('modal-overlay')) closeModal();
  });
  $('lancamento-form').addEventListener('submit', onFormSubmit);

  // PIX Modal
  $('btn-open-pix').addEventListener('click', () => {
    $('modal-pix-overlay').classList.remove('hidden');
    $('main-content').setAttribute('aria-hidden', 'true');
    setTimeout(() => $('modal-pix-close').focus(), 100);
  });
  const closePixModal = () => {
    $('modal-pix-overlay').classList.add('hidden');
    $('main-content').removeAttribute('aria-hidden');
    $('btn-open-pix').focus();
  };
  $('modal-pix-close').addEventListener('click', closePixModal);
  $('modal-pix-overlay').addEventListener('click', e => {
    if (e.target === $('modal-pix-overlay')) closePixModal();
  });
  $('btn-copy-pix').addEventListener('click', (e) => {
    const key = e.currentTarget.dataset.key;
    if (navigator.clipboard && window.isSecureContext) {
      navigator.clipboard.writeText(key).then(() => showToast('Chave PIX copiada!'));
    } else {
      const ta = document.createElement('textarea');
      ta.value = key;
      ta.style.position = 'absolute';
      ta.style.opacity = '0';
      document.body.appendChild(ta);
      ta.select();
      document.execCommand('copy');
      document.body.removeChild(ta);
      showToast('Chave PIX copiada!');
    }
  });


  // Máscara de Moeda (Real PT-BR)
  // Transforma input numérico (ex: 12345) em string formatada (ex: R$ 123,45)
  $('form-valor').addEventListener('input', e => {
    let value = e.target.value.replace(/\D/g, ''); // Remove tudo que não é dígito
    value = (value / 100).toFixed(2).replace('.', ','); // Trata centavos
    value = value.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.'); // Adiciona pontos de milhar
    e.target.value = value ? 'R$ ' + value : '';
  });

  // Máscara do campo Dia: apenas 2 dígitos, range 1-31
  $('form-dia').addEventListener('input', e => {
    let v = e.target.value.replace(/\D/g, '').slice(0, 2);
    if (v && parseInt(v) > 31) v = '31';
    if (v && parseInt(v) < 0) v = '';
    e.target.value = v;
  });


  // Atualiza subcategorias quando a categoria muda
  $('form-categoria').addEventListener('change', () => {
    populateSubcategoriaSelect($('form-categoria').value, null);
  });
}

function openCreateModal() {
  state.editingId = null;
  state.editingAno = null;
  $('modal-title').textContent = 'Novo Lançamento';
  $('btn-save').textContent = 'Salvar';
  $('lancamento-form').reset();
  $('form-id').value = '';
  $('form-mes').value = state.mes;
  $('form-dia').value = '';

  const cat = state.categoria || 'GASTO';
  $('form-categoria').value = cat;
  populateSubcategoriaSelect(cat, null);

  // Reseta parcelas
  $('group-parcelas').classList.remove('hidden');
  $('form-parcelas').value = 1;

  showModal();
}

function openEditModal(id) {
  const item = state.lancamentos.find(l => l.id === id);
  if (!item) return;

  state.editingId = id;
  state.editingAno = item.ano;
  $('modal-title').textContent = 'Editar Lançamento';
  $('btn-save').textContent = 'Atualizar';
  $('form-id').value = id;
  $('form-descricao').value = item.descricao || '';
  $('form-dia').value = item.dia || '';
  
  // Seta valor formatado
  const val = parseFloat(item.valor).toFixed(2).replace('.', ',');
  $('form-valor').value = 'R$ ' + val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.');

  $('form-mes').value = item.mes;
  $('form-categoria').value = item.categoria;

  // Popula subcategorias e mantém o valor atual selecionado
  populateSubcategoriaSelect(item.categoria, item.subcategoria);

  // Exibe sempre o campo de parcelas (conforme plano) e preenche o valor
  $('group-parcelas').classList.remove('hidden');
  $('form-parcelas').value = item.totalParcelas || 1;

  showModal();
}

function showModal() {
  $('modal-overlay').classList.remove('hidden');
  $('main-content').setAttribute('aria-hidden', 'true');
  setTimeout(() => $('form-subcategoria').focus(), 100);
}

function closeModal() {
  $('modal-overlay').classList.add('hidden');
  $('main-content').removeAttribute('aria-hidden');
  state.editingId = null;
  // Retorna o foco ao botão que geralmente abriu
  const addRowBtn = $('btn-add-row');
  if (addRowBtn && !addRowBtn.closest('.hidden')) addRowBtn.focus();
}

async function onFormSubmit(e) {
  e.preventDefault();

  const subcategoria = $('form-subcategoria').value;
  if (!subcategoria) {
    showToast('Selecione uma subcategoria.', 'error');
    return;
  }

  const rawValor = $('form-valor').value.replace('R$ ', '').replace(/\./g, '').replace(',', '.');
  const valor = parseFloat(rawValor);

  if (isNaN(valor) || valor <= 0) {
    showToast('Informe um valor válido maior que zero.', 'error');
    return;
  }

  const diaRaw = $('form-dia').value;
  const dia = diaRaw ? parseInt(diaRaw) : null;
  if (dia !== null && (isNaN(dia) || dia < 1 || dia > 31)) {
    showToast('O dia deve ser um número entre 1 e 31.', 'error');
    return;
  }

  const ano = state.editingId ? (state.editingAno || state.ano) : state.ano;
  const dto = {
    subcategoria,
    descricao:  $('form-descricao').value.trim() || subcategoria,
    valor:      valor,
    mes:        parseInt($('form-mes').value),
    ano:        ano,
    dia:        dia,
    categoria:  $('form-categoria').value,
    parcelas:   parseInt($('form-parcelas').value || 1),
  };

  const btn = $('btn-save');
  btn.textContent = 'Salvando...';
  btn.disabled = true;

  try {
    if (state.editingId) {
      await Api.atualizarLancamento(state.editingId, dto);
      showToast('Lançamento atualizado!', 'success');
    } else {
      await Api.criarLancamento(dto);
      showToast('Lançamento criado!', 'success');
    }
    _dashboardData = null; // Invalida o cache do dashboard
    closeModal();
    if (state.view === 'dashboard') {
      loadDashboard();
    } else {
      loadTabela();
    }
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    btn.textContent = state.editingId ? 'Atualizar' : 'Salvar';
    btn.disabled = false;
  }
}

// ─── EXCLUIR ─────────────────────────────────────────────────────────────────
async function excluir(id) {
  const item = state.lancamentos.find(l => l.id === id);
  if (!item) return;

  const label = `"${item.subcategoria}"`;
  let excluirProximos = !!(item.grupoId && item.parcelaActual);

  const msg = excluirProximos 
    ? `Excluir o lançamento ${label} e TODAS as parcelas futuras desta série?`
    : `Excluir o lançamento ${label}?`;

  if (!confirm(msg)) return;

  try {
    await Api.excluirLancamento(id, excluirProximos);
    _dashboardData = null; // Invalida o cache do dashboard
    
    if (excluirProximos) {
      loadTabela();
    } else {
      const row = $(`row-${id}`);
      if (row) {
        row.style.opacity = '0';
        row.style.transition = 'opacity 0.3s';
        setTimeout(() => loadTabela(), 300);
      } else {
        loadTabela();
      }
    }
    showToast(excluirProximos ? 'Série de lançamentos excluída.' : 'Lançamento excluído.', 'success');
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// ─── TOAST ───────────────────────────────────────────────────────────────────
let toastTimer = null;

function showToast(message, type = 'success') {
  const toast = $('toast');
  const icon = type === 'success' 
    ? `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" style="margin-right:8px"><polyline points="20 6 9 17 4 12"/></svg>`
    : `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" style="margin-right:8px"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>`;
  
  toast.innerHTML = icon;
  const span = document.createElement('span');
  span.textContent = message;
  toast.appendChild(span);
  toast.className = `toast toast--${type}`;
  toast.classList.remove('hidden');

  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.add('hidden'), 3500);
}

// ─── Utils ────────────────────────────────────────────────────────────────────
function fmtCurrency(value) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
  }).format(value || 0);
}

function escHtml(str) {
  return String(str || '').replace(/[&<>"']/g, c =>
    ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])
  );
}

// ─── BACKUP E RESTAURAÇÃO ─────────────────────────────────────────────────────
function initBackup() {
  $('btn-export-backup')?.addEventListener('click', exportBackup);
  $('btn-import-backup')?.addEventListener('click', () => {
    if (window.javaBridge) {
        window.javaBridge.importFile();
    } else {
        $('file-import-backup').click();
    }
  });
  $('file-import-backup')?.addEventListener('change', handleImportBackup);
}

async function exportBackup() {
  const btn = $('btn-export-backup');
  const originalHtml = btn.innerHTML;
  
  try {
    const msg = "DEFINA UMA SENHA PARA ESTE BACKUP:\n\n" +
                "IMPORTANTE! Esta senha será necessária para restaurar seus dados futuramente.\n" +
                "Se você esquecê-la, NÃO será possível recuperar este arquivo de backup.\n" +
                "Digite a senha desejada:";
    const pwd = prompt(msg);
    if (!pwd) return;

    btn.innerHTML = 'Gerando...';
    btn.disabled = true;

    // No modo nativo (JavaBridge), pedimos ao Java para abrir o diálogo de salvar
    if (window.javaBridge) {
      window.javaBridge.saveFile('/api/backup/export', `mytwocents_backup_${new Date().toISOString().split('T')[0]}.mtc`, pwd);
      showToast('O explorador de arquivos será aberto.');
      return;
    }

    // Modo Web puro (fallback)
    const res = await fetch('/api/backup/export', {
      headers: { 'X-Backup-Password': pwd }
    });
    if (!res.ok) throw new Error('Falha ao exportar banco de dados.');
    
    const blob = await res.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `mytwocents_backup_${new Date().toISOString().split('T')[0]}.mtc`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    showToast('Backup exportado com sucesso!');
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    btn.innerHTML = originalHtml;
    btn.disabled = false;
  }
}

async function handleImportBackup(e) {
  const file = e.target.files[0];
  if (!file) return;

  const pwd = prompt("Digite a senha deste backup para desencriptar:");
  if (!pwd) {
    e.target.value = '';
    return;
  }

  if (!confirm('ATENÇÃO: A restauração substituirá TODOS os dados atuais. Deseja continuar?')) {
    e.target.value = '';
    return;
  }

  const formData = new FormData();
  formData.append('file', file);
  
  const btn = $('btn-import-backup');
  const originalHtml = btn.innerHTML;
  btn.innerHTML = 'Restaurando...';
  btn.disabled = true;

  try {
    const res = await fetch('/api/backup/import', {
      method: 'POST',
      headers: { 'X-Backup-Password': pwd },
      body: formData
    });
    
    const msg = await res.text();
    if (!res.ok) {
        throw new Error(msg || 'Falha ao restaurar banco.');
    }
    
    showToast('Backup restaurado! Recarregando sistema...');
    setTimeout(() => location.reload(), 1500);
  } catch (err) {
    showToast(err.message, 'error');
  } finally {
    e.target.value = '';
    btn.innerHTML = originalHtml;
    btn.disabled = false;
  }
}
