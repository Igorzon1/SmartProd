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
      resultado.textContent = "Informe o preço pago e o tamanho da embalagem.";
      return;
    }
    const naUnidadeDoInsumo = (tamanho * BASE[unidadeEmbalagem.value]) / BASE[unidadeMedida.value];
    const custo = Math.round((preco / naUnidadeDoInsumo) * 10000) / 10000;
    custoUnitario.value = custo.toFixed(4);
    resultado.textContent = `${reais(preco, 2)} por ${tamanho.toLocaleString("pt-BR")} ${ROTULO[unidadeEmbalagem.value]}`
      + ` = ${reais(custo, 4)} por ${ROTULO[unidadeMedida.value]}. O custo acima foi preenchido.`;
  }

  ajuda.addEventListener("toggle", () => ajuda.open && atualizarMedidas());
  unidadeMedida.addEventListener("change", () => (ajuda.open ? calcular() : atualizarMedidas()));
  for (const campo of [precoEmbalagem, tamanhoEmbalagem, unidadeEmbalagem]) campo.addEventListener("input", calcular);
  // Cada vez que o cadastro abre, a ajuda começa fechada e vazia.
  document.querySelector("#editor").addEventListener("close", () => {
    ajuda.open = false;
    resultado.textContent = "";
  });
}

prepararAjudaDePreco();
