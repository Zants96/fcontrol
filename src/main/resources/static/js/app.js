/**
 * app.js – Lógica principal da SPA FControl
 */

// ─── Subcategorias por Categoria ─────────────────────────────────────────────
const SUBCATEGORIAS = {
  RECEITA: [
    '13º Salário', 'Férias', 'Outras Receitas', 'Participação nos Lucros', 
    'Resgate de Investimentos', 'Restituição de IR', 'Salário', 'Vendas'
  ],
  GASTO: [
    'Água', 'Alimentação', 'Aluguel', 'Cartão de Crédito', 'Consultas', 
    'Educação', 'Empréstimo', 'Investimentos', 'Lazer', 'Manutenção/Reparos', 
    'Medicamentos', 'Outros', 'Pets', 'Presentes / Doações', 'Prestações', 
    'Saúde & Beleza', 'Taxas/Impostos', 'Transporte', 'Vestuário', 'Viagens'
  ],
  GASTO_FIXO: [
    'Água', 'Aluguel/Prestação', 'Condomínio', 'Energia/Luz', 'Impostos', 
    'Internet', 'Investimentos', 'Outros', 'Seguro', 'Seguro Residencial', 
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
  initMenuToggle();
  initExport();
  initMonthlyDashboard();
  navigateTo('dashboard');
});

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
  };
  $('page-title').textContent = titles[view] || view;

  $('view-dashboard').classList.add('hidden');
  $('view-tabela').classList.add('hidden');

  if (view === 'dashboard') {
    $('view-dashboard').classList.remove('hidden');
    loadDashboard();
  } else {
    state.categoria = CATEGORIA_VIEW[view];
    $('view-tabela').classList.remove('hidden');
    buildMonthTabs();
    loadTabela();
  }
}

function refreshView() {
  navigateTo(state.view);
}

// ─── Exportação ──────────────────────────────────────────────────────────────
function buildExportUrl(format) {
  // Dashboard exporta o ano inteiro, sem filtro de mês
  const mes = state.view === 'dashboard' ? 0 : state.mes;
  return `/api/lancamentos/export/${format}?ano=${state.ano}&mes=${mes}&view=${state.view}`;
}

function buildFileName(format) {
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
  let name = `FControl - ${state.ano}`;
  // Só inclui mês no nome se NÃO for dashboard
  if (state.view !== 'dashboard' && state.mes > 0) {
    name += ` - ${MESES_NOME[state.mes - 1]}`;
  }
  name += ` - ${VIEW_NOMES[state.view] || 'Geral'}`;
  return `${name}.${format}`;
}

function triggerExport(format) {
  const url = buildExportUrl(format);
  const fileName = buildFileName(format);

  // Ambiente JavaFX Desktop: usa FileChooser nativo do sistema
  if (window.javaBridge) {
    window.javaBridge.saveFile(url, fileName);
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
  $('btn-export-csv').addEventListener('click', () => triggerExport('csv'));
  $('btn-export-pdf').addEventListener('click', () => triggerExport('pdf'));
}

// ─── Mobile Menu ─────────────────────────────────────────────────────────────
function initMenuToggle() {
  $('menu-toggle').addEventListener('click', () => {
    $('sidebar').classList.toggle('open');
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
    const mesSelecionado = mesSel ? parseInt(mesSel.value) : (new Date().getMonth() + 1);
    renderMonthlyDashboard(data, mesSelecionado);
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
      state.mes = parseInt(tab.dataset.mes);
      container.querySelectorAll('.month-tab').forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      loadTabela();
    });
  });
}

async function loadTabela() {
  const tbody = $('table-body');
  tbody.innerHTML = `<tr><td colspan="4"><div class="loading-overlay"><div class="spinner"></div> Carregando...</div></td></tr>`;
  $('table-total').innerHTML = '<strong>Calculando...</strong>';

  try {
    const lancamentos = await Api.getLancamentos({
      ano: state.ano,
      mes: state.mes,
      categoria: state.categoria,
    });

    state.lancamentos = lancamentos;
    renderTabela(lancamentos);
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="4"><div class="empty-state"><div class="empty-icon">⚠️</div><p>${err.message}</p></div></td></tr>`;
    showToast(err.message, 'error');
  }
}

function renderTabela(lancamentos) {
  const tbody = $('table-body');

  if (lancamentos.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="4">
          <div class="empty-state">
            <div class="empty-icon">📭</div>
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
        <td class="col-actions">
          <div class="action-btns">
            <button class="action-btn action-btn--edit" title="Editar" onclick="openEditModal(${l.id})">✏️</button>
            <button class="action-btn action-btn--del"  title="Excluir" onclick="excluir(${l.id})">🗑️</button>
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
  });
  $('modal-pix-close').addEventListener('click', () => {
    $('modal-pix-overlay').classList.add('hidden');
  });
  $('modal-pix-overlay').addEventListener('click', e => {
    if (e.target === $('modal-pix-overlay')) $('modal-pix-overlay').classList.add('hidden');
  });
    const key = $('pix-key').textContent;
    // Fallback para navegadores que não suportam navigator.clipboard (ou contextos não-seguros)
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


  // Máscara de Moeda (Real PT-BR)
  // Transforma input numérico (ex: 12345) em string formatada (ex: R$ 123,45)
  $('form-valor').addEventListener('input', e => {
    let value = e.target.value.replace(/\D/g, ''); // Remove tudo que não é dígito
    value = (value / 100).toFixed(2).replace('.', ','); // Trata centavos
    value = value.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.'); // Adiciona pontos de milhar
    e.target.value = value ? 'R$ ' + value : '';
  });


  // Atualiza subcategorias quando a categoria muda
  $('form-categoria').addEventListener('change', () => {
    populateSubcategoriaSelect($('form-categoria').value, null);
  });
}

function openCreateModal() {
  state.editingId = null;
  $('modal-title').textContent = 'Novo Lançamento';
  $('btn-save').textContent = 'Salvar';
  $('lancamento-form').reset();
  $('form-id').value = '';
  $('form-mes').value = state.mes;

  const cat = state.categoria || 'GASTO';
  $('form-categoria').value = cat;
  populateSubcategoriaSelect(cat, null);

  showModal();
}

function openEditModal(id) {
  const item = state.lancamentos.find(l => l.id === id);
  if (!item) return;

  state.editingId = id;
  $('modal-title').textContent = 'Editar Lançamento';
  $('btn-save').textContent = 'Atualizar';
  $('form-id').value = id;
  $('form-descricao').value = item.descricao || '';
  
  // Seta valor formatado
  const val = parseFloat(item.valor).toFixed(2).replace('.', ',');
  $('form-valor').value = 'R$ ' + val.replace(/(\d)(?=(\d{3})+(?!\d))/g, '$1.');

  $('form-mes').value = item.mes;
  $('form-categoria').value = item.categoria;

  // Popula subcategorias e mantém o valor atual selecionado
  populateSubcategoriaSelect(item.categoria, item.subcategoria);

  showModal();
}

function showModal() {
  $('modal-overlay').classList.remove('hidden');
  setTimeout(() => $('form-subcategoria').focus(), 100);
}

function closeModal() {
  $('modal-overlay').classList.add('hidden');
  state.editingId = null;
}

async function onFormSubmit(e) {
  e.preventDefault();

  const subcategoria = $('form-subcategoria').value;
  if (!subcategoria) {
    showToast('Selecione uma subcategoria.', 'error');
    return;
  }

  const dto = {
    subcategoria,
    descricao:  $('form-descricao').value.trim() || subcategoria,
    valor:      parseFloat($('form-valor').value.replace('R$ ', '').replace(/\./g, '').replace(',', '.')),
    mes:        parseInt($('form-mes').value),
    ano:        state.ano,
    categoria:  $('form-categoria').value,
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
  const label = item ? `"${item.subcategoria}"` : `#${id}`;
  if (!confirm(`Excluir o lançamento ${label}?`)) return;

  try {
    await Api.excluirLancamento(id);
    const row = $(`row-${id}`);
    if (row) {
      row.style.opacity = '0';
      row.style.transition = 'opacity 0.3s';
      setTimeout(() => loadTabela(), 300);
    } else {
      loadTabela();
    }
    showToast('Lançamento excluído.', 'success');
  } catch (err) {
    showToast(err.message, 'error');
  }
}

// ─── TOAST ───────────────────────────────────────────────────────────────────
let toastTimer = null;

function showToast(message, type = 'success') {
  const toast = $('toast');
  toast.textContent = (type === 'success' ? '✅ ' : '❌ ') + message;
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
