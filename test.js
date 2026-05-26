function $(id) {
  return { value: '', style: {}, setAttribute: () => {}, classList: { remove: () => {} } };
}

function formatQtd(val) {
  return (val || 0).toLocaleString('pt-BR');
}

function fmtCurrency(val) {
  return "R$ 10,00";
}

function escHtml(str) { return str; }

async function editarLancamentoInvestimento(id, ativoId, ticker, qtd, preco, custos, op) {
  console.log("Called with:", {id, ativoId, ticker, qtd, preco, custos, op});
  try {
    $('inv-edit-id').value = id;
    $('inv-edit-ativo-id').value = ativoId;
    $('inv-edit-op').value = op;
    
    const formQtd = $('inv-edit-qtd');
    const formPreco = $('inv-edit-preco');
    const formCustos = $('inv-edit-custos');
    const groupQtd = $('inv-edit-group-qtd');
    const labelPreco = $('inv-edit-label-preco');
    
    if (op === 'DIVIDENDO') {
      groupQtd.style.display = 'none';
      formQtd.value = '';
      labelPreco.textContent = 'Valor do dividendo (R$)';
    } else {
      groupQtd.style.display = '';
      formQtd.value = formatQtd(qtd);
      labelPreco.textContent = 'Preço unitário (R$)';
    }
    
    formPreco.value = fmtCurrency(preco).replace('R$', '').trim();
    formCustos.value = fmtCurrency(custos).replace('R$', '').trim();
    
    // Exibe o modal
    $('inv-edit-modal-overlay').classList.remove('hidden');
    $('main-content').setAttribute('aria-hidden', 'true');
    console.log("Success!");
  } catch(e) {
    console.error("Error:", e);
  }
}

editarLancamentoInvestimento(1, 2, 'PETR4', 10, 40.5, 0, 'COMPRA');
