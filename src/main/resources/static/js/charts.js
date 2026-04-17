/**
 * charts.js – Gerenciamento dos gráficos Chart.js
 */

const MESES = ['Jan','Fev','Mar','Abr','Mai','Jun','Jul','Ago','Set','Out','Nov','Dez'];

const CHART_COLORS = {
  green:  '#10b981',
  greenLight: '#34d399',
  red:    '#f87171',
  blue:   '#60a5fa',
  purple: '#a78bfa',
  orange: '#fb923c',
  yellow: '#fbbf24',
  pink:   '#f472b6',
  cyan:   '#22d3ee',
  lime:   '#a3e635',
};

const DONUT_COLORS = Object.values(CHART_COLORS);

// Instâncias dos gráficos (para destruir antes de recriar)
let chartBar   = null;
let chartDonut = null;
let chartLine  = null;
let chartDonutMonthly = null;

const chartDefaults = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      labels: {
        color: '#94a3b8',
        font: { family: 'Inter', size: 11 },
        boxWidth: 12,
        padding: 14,
      },
    },
    tooltip: {
      backgroundColor: '#1a2235',
      titleColor: '#f1f5f9',
      bodyColor: '#94a3b8',
      borderColor: 'rgba(148,163,184,0.15)',
      borderWidth: 1,
      padding: 10,
      callbacks: {
        label: (ctx) => ` ${fmtCurrency(ctx.parsed.y ?? ctx.parsed)}`,
      },
    },
  },
  scales: {
    x: {
      ticks:  { color: '#475569', font: { family: 'Inter', size: 11 } },
      grid:   { color: 'rgba(148,163,184,0.06)' },
    },
    y: {
      ticks: {
        color: '#475569',
        font: { family: 'Inter', size: 11 },
        callback: (v) => 'R$ ' + fmtCompact(v),
      },
      grid: { color: 'rgba(148,163,184,0.08)' },
    },
  },
};

function destroyIfExists(instance) {
  if (instance) { try { instance.destroy(); } catch(_) {} }
}

/**
 * Gráfico de barras: Receitas vs Gastos por mês
 */
function renderBarChart(data) {
  destroyIfExists(chartBar);
  const ctx = document.getElementById('chart-bar').getContext('2d');

  const totalGastos = (data.gastosPorMes || []).map((g, i) =>
    (parseFloat(g) + parseFloat(data.assinaturasPorMes?.[i] ?? 0))
  );

  chartBar = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: MESES,
      datasets: [
        {
          label: 'Receitas',
          data: (data.receitasPorMes || []).map(parseFloat),
          backgroundColor: 'rgba(16, 185, 129, 0.7)',
          borderColor: '#10b981',
          borderWidth: 1,
          borderRadius: 5,
          borderSkipped: false,
        },
        {
          label: 'Gastos + Assinaturas',
          data: totalGastos,
          backgroundColor: 'rgba(248, 113, 113, 0.6)',
          borderColor: '#f87171',
          borderWidth: 1,
          borderRadius: 5,
          borderSkipped: false,
        },
      ],
    },
    options: {
      ...chartDefaults,
      plugins: { ...chartDefaults.plugins },
    },
  });
}

/**
 * Gráfico donut: Distribuição de gastos por subcategoria
 */
function renderDonutChart(data) {
  destroyIfExists(chartDonut);
  
  let canvas = document.getElementById('chart-donut');
  if (!canvas) {
    const parent = document.querySelector('.chart-wrapper--donut');
    if (parent) {
      parent.innerHTML = '<canvas id="chart-donut"></canvas>';
      canvas = document.getElementById('chart-donut');
    }
  }
  if (!canvas) return;

  const ctx = canvas.getContext('2d');

  const entries = Object.entries(data.gastosPorSubcategoria || {})
    .filter(([, v]) => parseFloat(v) > 0)
    .sort(([,a],[,b]) => parseFloat(b) - parseFloat(a))
    .slice(0, 10);

  if (entries.length === 0) {
    ctx.canvas.parentElement.innerHTML = '<div class="empty-state"><div class="empty-icon">📉</div><p>Sem dados de gastos</p></div>';
    return;
  }

  chartDonut = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: entries.map(([k]) => k),
      datasets: [{
        data: entries.map(([,v]) => parseFloat(v)),
        backgroundColor: DONUT_COLORS.slice(0, entries.length).map(c => c + 'cc'),
        borderColor: '#111827',
        borderWidth: 2,
        hoverBorderWidth: 0,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '65%',
      plugins: {
        legend: {
          position: 'right',
          labels: {
            color: '#94a3b8',
            font: { family: 'Inter', size: 10 },
            boxWidth: 10,
            padding: 8,
          },
        },
        tooltip: {
          ...chartDefaults.plugins.tooltip,
          callbacks: {
            label: (ctx) => ` ${ctx.label}: ${fmtCurrency(ctx.parsed)}`,
          },
        },
      },
    },
  });
}

/**
 * Gráfico de linha: Evolução do saldo mensal
 */
function renderLineChart(data) {
  destroyIfExists(chartLine);
  const ctx = document.getElementById('chart-line').getContext('2d');

  const saldos = (data.saldoPorMes || []).map(parseFloat);

  const gradient = ctx.createLinearGradient(0, 0, 0, 300);
  gradient.addColorStop(0,   'rgba(16,185,129,0.35)');
  gradient.addColorStop(0.7, 'rgba(16,185,129,0.05)');
  gradient.addColorStop(1,   'rgba(16,185,129,0)');

  chartLine = new Chart(ctx, {
    type: 'line',
    data: {
      labels: MESES,
      datasets: [{
        label: 'Saldo Mensal',
        data: saldos,
        borderColor: '#10b981',
        backgroundColor: gradient,
        borderWidth: 2.5,
        pointBackgroundColor: saldos.map(v => v >= 0 ? '#34d399' : '#f87171'),
        pointBorderColor: '#111827',
        pointBorderWidth: 2,
        pointRadius: 5,
        pointHoverRadius: 7,
        fill: true,
        tension: 0.4,
      }],
    },
    options: {
      ...chartDefaults,
      plugins: {
        ...chartDefaults.plugins,
        tooltip: {
          ...chartDefaults.plugins.tooltip,
          callbacks: {
            label: (ctx) => ` Saldo: ${fmtCurrency(ctx.parsed.y)}`,
          },
        },
      },
      scales: {
        ...chartDefaults.scales,
        y: {
          ...chartDefaults.scales.y,
          ticks: {
            ...chartDefaults.scales.y.ticks,
            callback: (v) => 'R$ ' + fmtCompact(v),
          },
        },
      },
    },
  });
}

/**
 * Renderiza o ranking Top 5 gastos
 */
function renderTopGastos(topGastos) {
  const container = document.getElementById('top-gastos');
  if (!topGastos || topGastos.length === 0) {
    container.innerHTML = '<div class="empty-state"><div class="empty-icon">🎯</div><p>Sem dados ainda</p></div>';
    return;
  }

  const max = Math.max(...topGastos.map(t => parseFloat(t.valor)));

  container.innerHTML = topGastos.map((item, i) => {
    const pct = max > 0 ? (parseFloat(item.valor) / max * 100).toFixed(1) : 0;
    const colors = ['#10b981','#34d399','#60a5fa','#a78bfa','#fb923c'];
    return `
      <div class="top-item">
        <div class="top-rank">${i + 1}</div>
        <div class="top-bar-wrapper">
          <div class="top-label">${escHtml(item.subcategoria)}</div>
          <div class="top-bar-bg">
            <div class="top-bar-fill" style="width:${pct}%; background: ${colors[i]}"></div>
          </div>
        </div>
        <div class="top-value">${fmtCurrency(parseFloat(item.valor))}</div>
      </div>
    `;
  }).join('');
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function fmtCurrency(value) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
  }).format(value || 0);
}

function fmtCompact(value) {
  if (Math.abs(value) >= 1000) {
    return new Intl.NumberFormat('pt-BR', {
      notation: 'compact',
      maximumFractionDigits: 1,
    }).format(value);
  }
  return new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 0 }).format(value);
}

function escHtml(str) {
  return String(str).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
}

// ─── DASHBOARD MENSAL ──────────────────────────────────────────────────────

let _dashboardData = null; // Cache do DTO para uso no monthly

function initMonthlyDashboard() {
  const sel = document.getElementById('select-dash-mes');
  if (!sel) return;
  
  const MESES_FULL = [
    'Janeiro','Fevereiro','Março','Abril','Maio','Junho',
    'Julho','Agosto','Setembro','Outubro','Novembro','Dezembro'
  ];
  
  sel.innerHTML = '';
  MESES_FULL.forEach((nome, i) => {
    const opt = document.createElement('option');
    opt.value = i + 1;
    opt.textContent = nome;
    if (i + 1 === new Date().getMonth() + 1) opt.selected = true;
    sel.appendChild(opt);
  });
  
  sel.addEventListener('change', () => {
    if (_dashboardData) {
      renderMonthlyDashboard(_dashboardData, parseInt(sel.value));
    }
  });
}

async function renderMonthlyDashboard(data, mes) {
  _dashboardData = data;
  const idx = mes - 1;

  // Cards mensais
  const receitas = parseFloat(data.receitasPorMes?.[idx] ?? 0);
  const gastos = parseFloat(data.gastosPorMes?.[idx] ?? 0);
  const assinaturas = parseFloat(data.assinaturasPorMes?.[idx] ?? 0);
  const saldo = receitas - gastos - assinaturas;

  document.getElementById('card-m-receitas').textContent = fmtCurrency(receitas);
  document.getElementById('card-m-gastos').textContent = fmtCurrency(gastos);
  document.getElementById('card-m-assinaturas').textContent = fmtCurrency(assinaturas);
  
  const saldoEl = document.getElementById('card-m-saldo');
  saldoEl.textContent = fmtCurrency(saldo);
  saldoEl.style.color = saldo >= 0 ? '#10b981' : '#f87171';

  // Buscar lançamentos do mês para montar o donut e top 5
  try {
    const ano = data.ano || new Date().getFullYear();
    const res = await fetch(`/api/lancamentos?ano=${ano}&mes=${mes}`);
    const lancamentos = await res.json();
    
    // Filtrar apenas gastos (GASTO, GASTO_FIXO, ASSINATURA)
    const saidas = lancamentos.filter(l => l.categoria !== 'RECEITA');
    
    // Agrupar por subcategoria
    const porSubcat = {};
    saidas.forEach(l => {
      const sub = l.subcategoria || 'Outros';
      porSubcat[sub] = (porSubcat[sub] || 0) + parseFloat(l.valor);
    });

    renderMonthlyDonut(porSubcat);
    renderMonthlyTopGastos(porSubcat);
  } catch (err) {
    console.error('Erro ao carregar dados mensais:', err);
  }
}

function renderMonthlyDonut(gastosPorSubcategoria) {
  destroyIfExists(chartDonutMonthly);
  
  let canvas = document.getElementById('chart-donut-monthly');
  if (!canvas) {
    const parent = document.querySelector('#view-dashboard .charts-row:last-of-type .chart-wrapper--donut');
    if (parent) {
      parent.innerHTML = '<canvas id="chart-donut-monthly"></canvas>';
      canvas = document.getElementById('chart-donut-monthly');
    }
  }
  if (!canvas) return;

  const ctx = canvas.getContext('2d');

  const entries = Object.entries(gastosPorSubcategoria)
    .filter(([, v]) => v > 0)
    .sort(([,a],[,b]) => b - a)
    .slice(0, 10);

  if (entries.length === 0) {
    ctx.canvas.parentElement.innerHTML = '<div class="empty-state"><div class="empty-icon">📉</div><p>Sem gastos neste mês</p></div>';
    return;
  }

  chartDonutMonthly = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: entries.map(([k]) => k),
      datasets: [{
        data: entries.map(([,v]) => v),
        backgroundColor: DONUT_COLORS.slice(0, entries.length).map(c => c + 'cc'),
        borderColor: '#111827',
        borderWidth: 2,
        hoverBorderWidth: 0,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '65%',
      plugins: {
        legend: {
          position: 'right',
          labels: {
            color: '#94a3b8',
            font: { family: 'Inter', size: 10 },
            boxWidth: 10,
            padding: 8,
          },
        },
        tooltip: {
          ...chartDefaults.plugins.tooltip,
          callbacks: {
            label: (ctx) => ` ${ctx.label}: ${fmtCurrency(ctx.parsed)}`,
          },
        },
      },
    },
  });
}

function renderMonthlyTopGastos(gastosPorSubcategoria) {
  const container = document.getElementById('top-gastos-monthly');
  if (!container) return;

  const entries = Object.entries(gastosPorSubcategoria)
    .filter(([, v]) => v > 0)
    .sort(([,a],[,b]) => b - a)
    .slice(0, 5);

  if (entries.length === 0) {
    container.innerHTML = '<div class="empty-state"><div class="empty-icon">🎯</div><p>Sem dados neste mês</p></div>';
    return;
  }

  const max = entries[0][1];
  const colors = ['#10b981','#34d399','#60a5fa','#a78bfa','#fb923c'];

  container.innerHTML = entries.map(([sub, val], i) => {
    const pct = max > 0 ? (val / max * 100).toFixed(1) : 0;
    return `
      <div class="top-item">
        <div class="top-rank">${i + 1}</div>
        <div class="top-bar-wrapper">
          <div class="top-label">${escHtml(sub)}</div>
          <div class="top-bar-bg">
            <div class="top-bar-fill" style="width:${pct}%; background: ${colors[i]}"></div>
          </div>
        </div>
        <div class="top-value">${fmtCurrency(val)}</div>
      </div>
    `;
  }).join('');
}
