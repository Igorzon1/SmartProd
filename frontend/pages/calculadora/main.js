// Calculadora de receitas (RF006, RF007). Dupla 2. Gerente e Funcionário; nunca mostra custos.
import { exigirLogin } from '../../js/core/auth.js';
import { renderizarMenu } from '../../js/core/menu.js';
import { apiFetch } from '../../js/core/api.js';

/** true = dados falsos (reaproveita o mock das fichas). Troque para false quando D2-BE-03 estiver no ar. */
const USAR_MOCK = true;

const formato = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });
const $ = (id) => document.getElementById(id);

function el(tag, props = {}, ...filhos) {
  const elemento = document.createElement(tag);
  for (const [chave, valor] of Object.entries(props)) elemento[chave] = valor;
  elemento.append(...filhos);
  return elemento;
}

// ---------- Acesso a dados (API real ou mock) ----------

let fichasMock = null;
const arredondar3 = (n) => Math.round(n * 1000) / 1000;

async function carregarMock() {
  if (!fichasMock) {
    const url = new URL('../fichas-tecnicas/mock/fichas-tecnicas.json', import.meta.url);
    fichasMock = await fetch(url).then((r) => r.json());
  }
  return fichasMock;
}

function ingredientesCalculados(ficha, fator) {
  return ficha.ingredientes.map((i) => ({
    insumoId: i.insumoId,
    nome: i.insumoNome,
    unidadeMedida: i.unidadeMedida,
    quantidade: arredondar3(i.quantidadeBase * fator),
    percentualPadeiro: i.percentualPadeiro
  }));
}

const dados = {
  async listarFichas() {
    if (USAR_MOCK) return (await carregarMock()).map(({ id, nome, rendimentoPadrao }) => ({ id, nome, rendimentoPadrao }));
    return apiFetch('/api/fichas-tecnicas');
  },
  async buscarFicha(id) {
    if (USAR_MOCK) return (await carregarMock()).find((f) => f.id === id);
    return apiFetch(`/api/fichas-tecnicas/${id}`);
  },
  /** GET /api/fichas-tecnicas/{id}/calculo?unidades=N (contrato no docs/09). */
  async calcularPorDemanda(id, unidades) {
    if (USAR_MOCK) {
      const ficha = (await carregarMock()).find((f) => f.id === id);
      const fator = unidades / ficha.rendimentoPadrao;
      return { fichaId: id, unidades, fator: arredondar3(fator), ingredientes: ingredientesCalculados(ficha, fator) };
    }
    return apiFetch(`/api/fichas-tecnicas/${id}/calculo?unidades=${unidades}`);
  },
  /**
   * POST /api/fichas-tecnicas/{id}/calculo-por-estoque. O docs/09 mostra `"ingredientes": [ ... ]` sem detalhar;
   * assumido o mesmo formato do /calculo (insumoId, nome, unidadeMedida, quantidade, percentualPadeiro)
   * já referente às unidadesPossiveis. Confirmar com o backend da dupla 2.
   */
  async calcularPorEstoque(id, estoque) {
    if (USAR_MOCK) {
      const ficha = (await carregarMock()).find((f) => f.id === id);
      const quantidadeDe = (insumoId) => estoque.find((e) => e.insumoId === insumoId)?.quantidade ?? 0;
      let menor = Infinity;
      let limitante = null;
      for (const i of ficha.ingredientes) {
        const fatorI = quantidadeDe(i.insumoId) / i.quantidadeBase;
        if (fatorI < menor) {
          menor = fatorI;
          limitante = { insumoId: i.insumoId, nome: i.insumoNome };
        }
      }
      const unidadesPossiveis = Math.floor(menor * ficha.rendimentoPadrao);
      return {
        unidadesPossiveis,
        ingredienteLimitante: limitante,
        ingredientes: ingredientesCalculados(ficha, unidadesPossiveis / ficha.rendimentoPadrao)
      };
    }
    return apiFetch(`/api/fichas-tecnicas/${id}/calculo-por-estoque`, { metodo: 'POST', corpo: { estoque } });
  }
};

// ---------- Estado e utilitários de tela ----------

let fichaAtual = null; // detalhe da ficha selecionada (ingredientes para os campos de estoque)

const modoAtual = () => document.querySelector('input[name="modo"]:checked').value;
const fichaId = () => Number($('c-ficha').value);

function mostrarErro(texto) {
  $('mensagem').textContent = texto;
  $('mensagem').hidden = false;
}

function limparTudo() {
  $('mensagem').hidden = true;
  $('resultado').hidden = true;
}

async function comEspera(botao, tarefa) {
  botao.disabled = true;
  const rotulo = botao.textContent;
  botao.textContent = 'Calculando…';
  try {
    await tarefa();
  } catch (erro) {
    mostrarErro(`Não foi possível calcular: ${erro.message}`);
  } finally {
    botao.disabled = false;
    botao.textContent = rotulo;
  }
}

function montarCamposEstoque() {
  $('estoque-campos').replaceChildren(
    ...fichaAtual.ingredientes.map((i) => {
      const id = `est-${i.insumoId}`;
      return el('div', {},
        el('label', { className: 'rotulo', htmlFor: id, textContent: `${i.insumoNome} (${i.unidadeMedida})` }),
        el('input', { id, className: 'campo campo--num', type: 'number', min: '0', step: 'any', inputMode: 'decimal' }));
    })
  );
}

async function selecionarFicha() {
  limparTudo();
  fichaAtual = await dados.buscarFicha(fichaId());
  montarCamposEstoque();
}

function aplicarModo() {
  limparTudo();
  const porEstoque = modoAtual() === 'estoque';
  $('form-demanda').hidden = porEstoque;
  $('form-estoque').hidden = !porEstoque;
}

// ---------- Resultado ----------

function celula(texto, classe) {
  return el('td', { className: classe ?? '', textContent: texto });
}

function mostrarResultado({ resumo, detalhe, ingredientes, limitanteId, sobras }) {
  const alerta = resumo.alerta === true;
  $('resumo').className = alerta ? 'resumo resumo--alerta' : 'resumo';
  $('resumo').replaceChildren(resumo.texto, ...(detalhe ? [el('small', { textContent: detalhe })] : []));

  const colunas = ['Ingrediente', 'Quantidade', '% padeiro', ...(sobras ? ['Sobra no estoque'] : [])];
  $('res-cabecalho').replaceChildren(
    el('tr', {}, ...colunas.map((c, i) => el('th', { className: i > 0 ? 'num' : '', textContent: c })))
  );

  $('res-corpo').replaceChildren(
    ...ingredientes.map((i) => {
      const nome = el('td', { textContent: i.nome });
      if (i.insumoId === limitanteId) nome.append(el('span', { className: 'selo', textContent: 'limita' }));
      const linha = el('tr', { className: i.insumoId === limitanteId ? 'limitante' : '' },
        nome,
        celula(`${formato.format(i.quantidade)} ${i.unidadeMedida}`, 'num qtd'),
        celula(i.percentualPadeiro == null ? '—' : `${formato.format(i.percentualPadeiro)}%`, 'num'));
      if (sobras) {
        const sobra = sobras.get(i.insumoId);
        linha.append(celula(sobra == null ? '—' : `${formato.format(sobra)} ${i.unidadeMedida}`, 'num'));
      }
      return linha;
    })
  );
  $('resultado').hidden = false;
  $('titulo-resultado').focus();
}

// ---------- Ações ----------

async function calcularDemanda(evento) {
  evento.preventDefault();
  limparTudo();
  const unidades = Number($('c-unidades').value);
  if (!Number.isInteger(unidades) || unidades <= 0) {
    mostrarErro('Informe as unidades desejadas (número inteiro maior que zero).');
    $('c-unidades').focus();
    return;
  }
  await comEspera(evento.submitter ?? $('form-demanda').querySelector('button'), async () => {
    const r = await dados.calcularPorDemanda(fichaId(), unidades);
    mostrarResultado({
      resumo: { texto: `Para ${formato.format(r.unidades)} unidades` },
      detalhe: `Receita multiplicada por ${formato.format(r.fator)} (rendimento padrão: ${formato.format(fichaAtual.rendimentoPadrao)} un.)`,
      ingredientes: r.ingredientes
    });
  });
}

async function calcularEstoque(evento) {
  evento.preventDefault();
  limparTudo();
  const estoque = [];
  const faltando = [];
  for (const i of fichaAtual.ingredientes) {
    const campo = $(`est-${i.insumoId}`);
    const q = campo.value === '' ? NaN : Number(campo.value);
    if (!(q >= 0)) faltando.push(i.insumoNome);
    else estoque.push({ insumoId: i.insumoId, quantidade: q });
  }
  if (faltando.length) {
    mostrarErro(`Informe o estoque (0 ou mais) de: ${faltando.join(', ')}.`);
    return;
  }
  await comEspera(evento.submitter ?? $('form-estoque').querySelector('button'), async () => {
    const r = await dados.calcularPorEstoque(fichaId(), estoque);
    const ingredientes = r.ingredientes ?? [];
    const sobras = new Map(
      estoque.map((e) => [e.insumoId, Math.round((e.quantidade - (ingredientes.find((i) => i.insumoId === e.insumoId)?.quantidade ?? 0)) * 1000) / 1000])
    );
    const nenhuma = r.unidadesPossiveis === 0;
    mostrarResultado({
      resumo: {
        texto: nenhuma ? 'Estoque insuficiente para uma unidade.' : `Dá para produzir ${formato.format(r.unidadesPossiveis)} unidades`,
        alerta: nenhuma
      },
      detalhe: r.ingredienteLimitante ? `Ingrediente que limita: ${r.ingredienteLimitante.nome}` : null,
      ingredientes,
      limitanteId: r.ingredienteLimitante?.insumoId,
      sobras
    });
  });
}

// ---------- Início ----------

const sessao = exigirLogin(['GERENTE', 'FUNCIONARIO']);
if (sessao) {
  renderizarMenu($('menu'), sessao.usuario.perfil);
  $('aviso-mock').hidden = !USAR_MOCK;
  iniciar().catch((erro) => mostrarErro(`Não foi possível carregar as receitas: ${erro.message}`));
}

async function iniciar() {
  const fichas = await dados.listarFichas();
  if (fichas.length === 0) {
    mostrarErro('Nenhuma ficha técnica cadastrada. Peça ao gerente para cadastrar uma receita.');
    return;
  }
  $('c-ficha').replaceChildren(...fichas.map((f) => el('option', { value: String(f.id), textContent: f.nome })));
  const pedida = new URLSearchParams(location.search).get('fichaId'); // permite abrir direto de outra tela
  if (pedida && fichas.some((f) => String(f.id) === pedida)) $('c-ficha').value = pedida;

  $('c-ficha').addEventListener('change', () => selecionarFicha().catch((e) => mostrarErro(e.message)));
  document.querySelectorAll('input[name="modo"]').forEach((r) => r.addEventListener('change', aplicarModo));
  $('c-unidades').addEventListener('input', () => ($('resultado').hidden = true));
  $('form-demanda').addEventListener('submit', calcularDemanda);
  $('form-estoque').addEventListener('submit', calcularEstoque);
  await selecionarFicha();
}
