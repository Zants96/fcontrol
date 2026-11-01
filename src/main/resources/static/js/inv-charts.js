/**
 * inv-charts.js – Gráficos de distribuição, evolução e dividendos
 * Depende de: inv-constants.js, Chart.js (chart.umd.min.js)
 */

// ─── DONUT CHART ─────────────────────────────────────────────────────────

function renderInvDistChart(data) {
  state.lastDashboardData = data;
  const mode = state.distChartMode || 'tipo';

  const labels = [];
  const values = [];
  const idealValues = [];
  const colors = [];

  if (mode === 'tatica') {
    const taticaTotals = {};
    if (data.ativosPorTipo) {
      Object.values(data.ativosPorTipo).forEach(lista => {
        (lista || []).forEach(a => {
          let catKey = (a.categoriaTatica || 'RENDA').toString().toUpperCase().trim();
          catKey = catKey.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
          const val = parseFloat(a.valorTotal || 0);
          if (val > 0) {
            taticaTotals[catKey] = (taticaTotals[catKey] || 0) + val;
          }
        });
      });
    }

    CATEGORIA_TATICA_ORDER.forEach(catKey => {
      if (taticaTotals[catKey] && taticaTotals[catKey] > 0) {
        labels.push(CATEGORIA_TATICA_LABELS[catKey] || catKey);
        values.push(taticaTotals[catKey]);
        colors.push(CATEGORIA_TATICA_COLORS[catKey] || '#94a3b8');
      }
    });

    Object.keys(taticaTotals).forEach(catKey => {
      if (!CATEGORIA_TATICA_ORDER.includes(catKey) && taticaTotals[catKey] > 0) {
        labels.push(catKey);
        values.push(taticaTotals[catKey]);
        colors.push('#94a3b8');
      }
    });
  } else {
    const dist = data.distribuicaoPorTipo || {};
    const distIdeal = data.distribuicaoIdealPorTipo || {};

    TIPO_ATIVO_ORDER.forEach(tipo => {
      if (dist[tipo] && parseFloat(dist[tipo]) > 0) {
        labels.push(TIPO_ATIVO_LABELS[tipo] || tipo);
        values.push(parseFloat(dist[tipo]));
        colors.push(TIPO_ATIVO_COLORS[tipo] || '#666');
        if (distIdeal[tipo] && parseFloat(distIdeal[tipo]) > 0) {
          idealValues.push(parseFloat(distIdeal[tipo]));
        } else {
          idealValues.push(0);
        }
      }
    });
  }

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
  const totalIdeal = idealValues.reduce((a, b) => a + b, 0);
  const isLight = document.documentElement.getAttribute('data-theme') === 'light';
  const hasIdeal = idealValues.some(v => v > 0);
  const themeBorderColor = isLight ? '#ccd9ecff' : '#111827';

  const datasets = [];

  // Anel Externo: Alocação REAL (vibrante)
  datasets.push({
    label: 'Alocação Real',
    data: values,
    backgroundColor: colors.map(c => c),
    borderColor: themeBorderColor,
    borderWidth: 1,
    hoverBorderWidth: 0
  });

  // Anel Interno: Alocação IDEAL (40% alfa)
  if (hasIdeal) {
    datasets.push({
      label: 'Alocação Ideal',
      data: idealValues,
      backgroundColor: colors.map(c => c + '66'),
      borderColor: themeBorderColor,
      borderWidth: 1,
      hoverBorderWidth: 0
    });
  }

  _invDistChart = new Chart(ctx, {
    type: 'doughnut',
    data: { labels, datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '50%',
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
          backgroundColor: '#1a2235',
          titleColor: '#f1f5f9',
          bodyColor: '#94a3b8',
          borderColor: 'rgba(148,163,184,0.15)',
          borderWidth: 1,
          padding: 10,
          callbacks: {
            label: (ctx) => {
              const datasetIndex = ctx.datasetIndex;
              const label = ctx.label;
              const val = ctx.parsed || 0;
              const tot = datasetIndex === 0 ? total : totalIdeal;
              const pct = tot > 0 ? ((val / tot) * 100).toFixed(1) : 0;
              const tipoStr = datasetIndex === 0 ? 'Atual' : 'Ideal';
              return `${tipoStr} ${pct}% (${fmtCurrency(val)})`;
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
  const lucrosColors = lucros.map(v => v >= 0 ? '#34d399' : '#f87171');

  if (labels.length === 0) return;

  _invEvolucaoChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels,
      datasets: [
        {
          label: 'Valor aplicado',
          data: investido,
          backgroundColor: '#059669',
          borderRadius: 4
        },
        {
          label: 'Ganho de Capital',
          data: lucros,
          backgroundColor: lucrosColors,
          borderRadius: 4
        }
      ]
    },
    options: {
      responsive: true, maintainAspectRatio: false,
      interaction: { mode: 'index', intersect: false },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            title: (ctx) => 'Mês: ' + ctx[0].label,
            label: (context) => ` ${context.dataset.label}: ${fmtCurrency(context.raw)}`,
            footer: (contextItems) => {
              let valInvestido = 0;
              let valLucro = 0;
              contextItems.forEach(ci => {
                if (ci.dataset.label === 'Valor aplicado') valInvestido = ci.raw;
                if (ci.dataset.label === 'Ganho de Capital') valLucro = ci.raw;
              });
              return `\nPatrimônio Total: ${fmtCurrency(valInvestido + valLucro)}`;
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
        label: 'Proventos',
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
          callbacks: { label: (ctx) => ` ${fmtCurrency(ctx.raw)}` }
        }
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() } },
        y: {
          beginAtZero: true,
          grace: '10%',
          grid: { color: 'rgba(255,255,255,0.05)' },
          ticks: { color: getComputedStyle(document.documentElement).getPropertyValue('--text-muted').trim() }
        }
      }
    },
    plugins: [{
      id: 'barLabels',
      afterDatasetsDraw(chart) {
        const { ctx } = chart;
        ctx.save();
        chart.data.datasets.forEach((dataset, i) => {
          const meta = chart.getDatasetMeta(i);
          meta.data.forEach((bar, index) => {
            const val = dataset.data[index];
            if (val > 0) {
              ctx.fillStyle = '#94a3b8';
              ctx.font = '500 11px Inter, sans-serif';
              ctx.textAlign = 'center';
              ctx.textBaseline = 'bottom';
              ctx.fillText(fmtCurrency(val), bar.x, bar.y - 5);
            }
          });
        });
        ctx.restore();
      }
    }]
  });
}

// ─── MINI CHART DE CATEGORIA TÁTICA ──────────────────────────────────────

let _invDistTaticaMiniChart = null;

function renderMiniTaticaChart(allAtivos) {
  const ctx = document.getElementById('chart-inv-dist-tatica-card');
  if (!ctx) return;

  if (_invDistTaticaMiniChart) _invDistTaticaMiniChart.destroy();

  const taticaTotals = {};
  allAtivos.forEach(a => {
    let catKey = (a.categoriaTatica || 'RENDA').toString().toUpperCase().trim();
    catKey = catKey.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
    taticaTotals[catKey] = (taticaTotals[catKey] || 0) + (a.valorTotal || 0);
  });

  const labels = [];
  const values = [];
  const colors = [];

  CATEGORIA_TATICA_ORDER.forEach(catKey => {
    if (taticaTotals[catKey] && taticaTotals[catKey] > 0) {
      labels.push(CATEGORIA_TATICA_LABELS[catKey] || catKey);
      values.push(taticaTotals[catKey]);
      colors.push(CATEGORIA_TATICA_COLORS[catKey] || '#94a3b8');
    }
  });

  if (values.length === 0) return;

  const isLight = document.documentElement.getAttribute('data-theme') === 'light';
  const themeBorderColor = isLight ? '#ffffff' : '#111827';

  _invDistTaticaMiniChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels,
      datasets: [{
        data: values,
        backgroundColor: colors,
        borderColor: themeBorderColor,
        borderWidth: 1
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '55%',
      plugins: {
        legend: {
          position: 'right',
          labels: { color: '#94a3b8', font: { family: 'Inter', size: 12 }, boxWidth: 10, padding: 8 }
        },
        tooltip: {
          backgroundColor: '#1a2235',
          callbacks: { label: (c) => `${c.label}: ${fmtCurrency(c.parsed)}` }
        }
      }
    }
  });
}
