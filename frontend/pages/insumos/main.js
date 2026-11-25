// Tela de insumos (RF004). Só Gerente; a lógica comum fica em js/core/cadastro.js.
import { iniciarCadastro } from "../../js/core/cadastro.js";

iniciarCadastro("insumos");

/** Medidas de embalagem possíveis para cada unidade do insumo, com quanto valem na unidade base (g, ml, un). */
const MEDIDAS = {
  G: [["KG", "kg", 1000], ["G", "g", 1]],
  KG: [["KG", "kg", 1000], ["G", "g", 1]],
  ML: [["L", "L", 1000], ["ML", "ml", 1]],
  L: [["L", "L", 1000], ["ML", "ml", 1]],
  UN: [["UN", "un", 1]],
};
const BASE = { G: 1, KG: 1000, ML: 1, L: 1000, UN: 1 };
const ROTULO = { G: "g", KG: "kg", ML: "ml", L: "L", UN: "un" };
const reais = (valor, casas) => new Intl.NumberFormat("pt-BR", {
  style: "currency", currency: "BRL", minimumFractionDigits: casas, maximumFractionDigits: casas,
}).format(valor);

/**
 * O dono sabe quanto pagou pelo saco (R$ 148,37 por 50 kg), não o custo por grama. A ajuda faz a conta e
 * preenche o "Custo por unidade" com 4 casas, que é o que o sistema guarda.
 */
function prepararAjudaDePreco() {
  const form = document.querySelector("#formulario");
  const ajuda = document.querySelector("#ajuda-preco");
  if (!form || !ajuda) return;
  const { unidadeMedida, custoUnitario, precoEmbalagem, tamanhoEmbalagem, unidadeEmbalagem } = form.elements;
  const resultado = document.querySelector("#resultado-preco");
  const dicaCustoUnitario = custoUnitario.parentElement.querySelector(".hint");
  const editor = document.querySelector("#editor");

  function atualizarDicaCustoUnitario() {
    const unidade = unidadeMedida.value;
    const quantidade = unidade === "UN" ? "1 unidade" : `1 ${ROTULO[unidade]}`;
    dicaCustoUnitario.textContent = `Informe o custo de ${quantidade}; use até quatro casas decimais.`;
  }

  function atualizarMedidas() {
    const medidas = MEDIDAS[unidadeMedida.value] ?? [];
    if ([...unidadeEmbalagem.options].map((o) => o.value).join() === medidas.map((m) => m[0]).join()) return;
    unidadeEmbalagem.replaceChildren(...medidas.map(([valor, rotulo]) => new Option(rotulo, valor)));
  }

  function calcular() {
    atualizarMedidas();
    const preco = Number(precoEmbalagem.value);
    const tamanho = Number(tamanhoEmbalagem.value);
    if (!(preco > 0) || !(tamanho > 0) || precoEmbalagem.value === "" || tamanhoEmbalagem.value === "") {
      const mensagem = document.createElement("p");
      mensagem.className = "ajuda-preco__mensagem";
      mensagem.textContent = "Informe o preço pago e o tamanho da embalagem.";
      resultado.replaceChildren(mensagem);
      return;
    }
    const codigoUnidade = unidadeMedida.value;
    const unidadeBase = codigoUnidade === "G" || codigoUnidade === "KG" ? "g"
      : codigoUnidade === "ML" || codigoUnidade === "L" ? "ml" : "un";
    const quantidadeBase = tamanho * BASE[unidadeEmbalagem.value];
    const custoPorUnidadeBase = preco / quantidadeBase;
    const naUnidadeDoInsumo = quantidadeBase / BASE[codigoUnidade];
    const custoExato = preco / naUnidadeDoInsumo;
    const custo = Math.round(custoExato * 10000) / 10000;
    custoUnitario.value = custo.toFixed(4);

    const titulo = document.createElement("strong");
    titulo.className = "ajuda-preco__titulo";
    titulo.textContent = "Custo estimado a partir da embalagem";
    const equivalencias = document.createElement("dl");
    equivalencias.className = "ajuda-preco__equivalencias";
    const valores = unidadeBase === "g"
      ? [["1 kg", 1000], ["100 g", 100], ["1 g", 1]]
      : unidadeBase === "ml"
        ? [["1 L", 1000], ["100 ml", 100], ["1 ml", 1]]
        : [["1 un", 1]];
    for (const [rotulo, quantidade] of valores) {
      const item = document.createElement("div");
      item.className = "ajuda-preco__equivalencia";
      const termo = document.createElement("dt");
      termo.textContent = `Por ${rotulo}`;
      const valor = document.createElement("dd");
      valor.textContent = reais(custoPorUnidadeBase * quantidade, 4);
      item.append(termo, valor);
      equivalencias.append(item);
    }

    const embalagem = document.createElement("p");
    embalagem.className = "ajuda-preco__base";
    embalagem.textContent = `${reais(preco, 2)} por ${tamanho.toLocaleString("pt-BR")} ${ROTULO[unidadeEmbalagem.value]}.`;
    const partes = [titulo, embalagem, equivalencias];
    if (unidadeBase !== "un") {
      const amostra = document.createElement("p");
      amostra.className = "ajuda-preco__estimativa";
      amostra.textContent = `Exemplo: 300 ${unidadeBase} custam aproximadamente ${reais(custoPorUnidadeBase * 300, 2)}.`;
      partes.push(amostra);
    }
    const precisao = document.createElement("p");
    precisao.className = "ajuda-preco__precisao";
    precisao.textContent = `O campo acima foi preenchido com ${reais(custo, 4)} por ${ROTULO[codigoUnidade]} (4 casas decimais). A estimativa usa o preço original da embalagem; pequenas diferenças podem aparecer nos custos da ficha.`;
    partes.push(precisao);
    resultado.replaceChildren(...partes);
  }

  ajuda.addEventListener("toggle", () => ajuda.open && atualizarMedidas());
  unidadeMedida.addEventListener("change", () => {
    atualizarDicaCustoUnitario();
    if (ajuda.open) calcular();
    else atualizarMedidas();
  });
  for (const campo of [precoEmbalagem, tamanhoEmbalagem, unidadeEmbalagem]) {
    campo.addEventListener("input", calcular);
    campo.addEventListener("change", calcular);
  }
  // Cada vez que o cadastro abre, a ajuda começa fechada e vazia.
  editor.addEventListener("close", () => {
    ajuda.open = false;
    resultado.textContent = "";
  });

  // O cadastro compartilhado redefine a unidade e a preenche ao abrir edição.
  // Observar a abertura garante que a dica acompanhe também esse valor inicial.
  new MutationObserver(() => {
    if (editor.open) atualizarDicaCustoUnitario();
  }).observe(editor, { attributes: true, attributeFilter: ["open"] });

  atualizarDicaCustoUnitario();
}

prepararAjudaDePreco();
