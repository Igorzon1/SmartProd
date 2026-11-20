// Tela de fichas técnicas (RF005, RF006). Dupla 2.
import { exigirLogin } from '../../js/core/auth.js';
import { renderizarMenu } from '../../js/core/menu.js';
import { apiFetch } from '../../js/core/api.js';
import { UNIDADES } from '../../js/core/formato.js';

/** false = API real (cadastro e edição no ar). true = dados falsos em mock/*.json, para mexer na tela sem backend. */
const USAR_MOCK = false;

const formatoNumero = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });
const formatoReais = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
/** Custo de uma unidade: um pão custa centavos, então mostra até 4 casas (R$ 0,1476). */
const formatoReaisUnidade = new Intl.NumberFormat('pt-BR', {
  style: 'currency', currency: 'BRL', minimumFractionDigits: 2, maximumFractionDigits: 4
});

/** Quanto vale 1 unidade do insumo em g (ou ml): a Porcentagem do Padeiro compara tudo na mesma base. */
const PARA_GRAMAS = { G: 1, ML: 1, KG: 1000, L: 1000 };

// ---------- Acesso a dados (API real ou mock) ----------

const mock = { fichas: null, insumos: null, proximoId: 100 };

async function carregarMock() {
  if (mock.fichas) return;
  const ler = (arquivo) => fetch(new URL(`mock/${arquivo}`, import.meta.url)).then((r) => r.json());
  [mock.fichas, mock.insumos] = await Promise.all([ler('fichas-tecnicas.json'), ler('insumos.json')]);
}

/** Mesma conta da API (docs/07 §2), usada só para o mock devolver percentualPadeiro. */
function percentuais(ingredientes) {
  const unidade = (i) => mock.insumos.find((x) => x.id === i.insumoId)?.unidadeMedida;
  const gramas = (i) => (PARA_GRAMAS[unidade(i)] ? i.quantidadeBase * PARA_GRAMAS[unidade(i)] : null);
  const farinha = ingredientes.filter((i) => i.ehFarinha).reduce((soma, i) => soma + (gramas(i) ?? 0), 0);
  return (i) => (farinha > 0 && gramas(i) !== null ? Math.round((gramas(i) / farinha) * 10000) / 100 : null);
}

function montarFichaMock(id, corpo) {
  const pct = percentuais(corpo.ingredientes);
  return {
    id,
    nome: corpo.nome,
    rendimentoPadrao: corpo.rendimentoPadrao,
    ingredientes: corpo.ingredientes.map((i) => {
      const insumo = mock.insumos.find((x) => x.id === i.insumoId);
      return { ...i, insumoNome: insumo.nome, unidadeMedida: insumo.unidadeMedida, percentualPadeiro: pct(i) };
    }),
    passos: corpo.passos
  };
}

const dados = {
  async listarFichas() {
    if (USAR_MOCK) {
      await carregarMock();
      return mock.fichas.map(({ id, nome, rendimentoPadrao }) => ({ id, nome, rendimentoPadrao }));
    }
    return apiFetch('/api/fichas-tecnicas');
  },
  async buscarFicha(id) {
    if (USAR_MOCK) {
      await carregarMock();
      return structuredClone(mock.fichas.find((f) => f.id === id));
    }
    return apiFetch(`/api/fichas-tecnicas/${id}`);
  },
  /** Custo de cada ficha (só Gerente). No mock não há custos: a lista mostra "—". */
  async listarCustos() {
    if (USAR_MOCK) return [];
    return apiFetch('/api/fichas-tecnicas/custos');
  },
  async listarInsumos() {
    if (USAR_MOCK) {
      await carregarMock();
      return mock.insumos;
    }
    return apiFetch('/api/insumos');
  },
  /**
   * ATENÇÃO: o docs/09 ainda não define o corpo de escrita. Assumido (confirmar com o backend da dupla 2):
   * POST /api/fichas-tecnicas e PUT /api/fichas-tecnicas/{id} com
   * { nome, rendimentoPadrao, ingredientes: [{ insumoId, quantidadeBase, ehFarinha }],
   *   passos: [{ ordem, etapa, descricao, duracaoSegundos }] }
   */
  async salvarFicha(id, corpo) {
    if (USAR_MOCK) {
      await carregarMock();
      if (id) {
        const indice = mock.fichas.findIndex((f) => f.id === id);
        mock.fichas[indice] = montarFichaMock(id, corpo);
        return mock.fichas[indice];
      }
      const nova = montarFichaMock(mock.proximoId++, corpo);
      mock.fichas.push(nova);
      return nova;
    }
    return apiFetch(id ? `/api/fichas-tecnicas/${id}` : '/api/fichas-tecnicas', {
      metodo: id ? 'PUT' : 'POST',
      corpo
    });
  }
};

// ---------- Utilitários de DOM ----------

function el(tag, props = {}, ...filhos) {
  const elemento = document.createElement(tag);
  for (const [chave, valor] of Object.entries(props)) {
    if (chave.startsWith('aria-')) elemento.setAttribute(chave, valor);
    else elemento[chave] = valor;
  }
  elemento.append(...filhos);
  return elemento;
}

const $ = (id) => document.getElementById(id);

function mostrarMensagem(tipo, titulo, itens = []) {
  const caixa = $('mensagem');
  caixa.className = `msg msg--${tipo}`;
  caixa.replaceChildren(titulo, ...(itens.length ? [el('ul', {}, ...itens.map((t) => el('li', { textContent: t })))] : []));
  caixa.hidden = false;
}

function limparMensagem() {
  $('mensagem').hidden = true;
}

function mostrarVista(nome) {
  $('vista-lista').hidden = nome !== 'lista';
  $('vista-form').hidden = nome !== 'form';
  $(nome === 'lista' ? 'titulo-lista' : 'titulo-form').focus();
}

// ---------- Lista ----------

async function carregarLista() {
  const corpo = $('lista-corpo');
  try {
    // Sem os custos a lista continua útil: as colunas de custo ficam com "—".
    const [fichas, custos] = await Promise.all([dados.listarFichas(), dados.listarCustos().catch(() => [])]);
    const custoDa = new Map(custos.map((c) => [c.fichaId, c]));
    $('lista-vazia').hidden = fichas.length > 0;
    corpo.replaceChildren(
      ...fichas.map((ficha) => {
        const editar = el('button', { className: 'btn btn--sec', type: 'button', textContent: 'Editar' });
        editar.setAttribute('aria-label', `Editar ${ficha.nome}`);
        editar.addEventListener('click', () => abrirFormulario(ficha.id));
        const custo = custoDa.get(ficha.id);
        return el('tr', {},
          el('td', { textContent: ficha.nome }),
          el('td', { className: 'num', textContent: formatoNumero.format(ficha.rendimentoPadrao) }),
          el('td', { className: 'num', textContent: custo ? formatoReaisUnidade.format(custo.custoUnidade) : '—' }),
          el('td', { className: 'num', textContent: custo?.custoPorKg != null ? formatoReais.format(custo.custoPorKg) : '—' }),
          el('td', {}, editar));
      })
    );
  } catch (erro) {
    mostrarMensagem('erro', `Não foi possível carregar as fichas: ${erro.message}`);
  }
}

// ---------- Formulário ----------

let fichaEditandoId = null;
let insumosDisponiveis = [];

function opcoesInsumo(select, selecionadoId) {
  select.replaceChildren(
    el('option', { value: '', textContent: 'Selecione…' }),
    ...insumosDisponiveis.map((i) => el('option', { value: String(i.id), textContent: i.nome, selected: i.id === selecionadoId }))
  );
}

function insumoPorId(id) {
  return insumosDisponiveis.find((i) => i.id === Number(id));
}

function unidadeDoInsumo(id) {
  return insumoPorId(id)?.unidadeMedida ?? '';
}

/** "G" → "g", "KG" → "kg": como o padeiro lê. */
function rotuloUnidade(codigo) {
  return UNIDADES[codigo] ?? codigo ?? '';
}

function criarLinhaIngrediente(ing = {}) {
  const select = el('select', { className: 'campo', required: true, 'aria-label': 'Insumo' });
  opcoesInsumo(select, ing.insumoId);
  const quantidade = el('input', {
    className: 'campo campo--num', type: 'number', min: '0', step: 'any', inputMode: 'decimal',
    value: ing.quantidadeBase ?? '', 'aria-label': 'Quantidade base'
  });
  const unidade = el('td', { className: 'unidade', textContent: rotuloUnidade(ing.unidadeMedida ?? unidadeDoInsumo(ing.insumoId)) });
  const farinha = el('input', { type: 'checkbox', checked: Boolean(ing.ehFarinha), 'aria-label': 'É farinha' });
  const pct = el('td', { className: 'pct' });
  const remover = el('button', { className: 'btn btn--perigo btn--icone', type: 'button', textContent: '✕' });
  remover.setAttribute('aria-label', 'Remover ingrediente');

  const linha = el('tr', {},
    el('td', {}, select),
    el('td', {}, quantidade),
    unidade,
    el('td', {}, el('label', { className: 'farinha' }, farinha, 'Farinha')),
    pct,
    el('td', {}, remover));
  linha.refs = { select, quantidade, farinha };

  select.addEventListener('change', () => {
    unidade.textContent = rotuloUnidade(unidadeDoInsumo(select.value));
    atualizarPercentuais();
  });
  [quantidade, farinha].forEach((c) => c.addEventListener('input', atualizarPercentuais));
  farinha.addEventListener('change', atualizarPercentuais);
  remover.addEventListener('click', () => {
    linha.remove();
    atualizarPercentuais();
  });
  linha.pct = pct;
  return linha;
}

/** Quantidade da linha em g (ou ml), ou null se o insumo é em unidade (UN) ou ainda não foi escolhido. */
function gramasDaLinha(linha) {
  const fator = PARA_GRAMAS[unidadeDoInsumo(linha.refs.select.value)];
  const quantidade = parseFloat(linha.refs.quantidade.value);
  return fator && quantidade >= 0 ? quantidade * fator : null;
}

/**
 * Prévia enquanto digita: a Porcentagem do Padeiro (farinhas somam 100%) e o resumo de massa e custo.
 * Mesma conta da API: kg vira g e L vira ml antes de dividir, e ingrediente em unidade (UN) fica sem %.
 */
function atualizarPercentuais() {
  const linhas = [...$('ingredientes-corpo').children];
  const somaFarinha = linhas
    .filter((l) => l.refs.farinha.checked)
    .reduce((soma, l) => soma + (gramasDaLinha(l) ?? 0), 0);
  for (const linha of linhas) {
    const gramas = gramasDaLinha(linha);
    linha.pct.textContent = somaFarinha > 0 && gramas !== null
      ? `${formatoNumero.format(Math.round((gramas / somaFarinha) * 1000) / 10)}%`
      : '—';
  }
  atualizarResumo(linhas);
}

/** Massa total, massa por unidade e custo (receita, unidade e kg), como o dashboard calcula. */
function atualizarResumo(linhas) {
  const rendimento = Number($('f-rendimento').value);
  const preenchidas = linhas.filter((l) => l.refs.select.value && parseFloat(l.refs.quantidade.value) > 0);
  const massaKg = preenchidas.reduce((soma, l) => soma + (gramasDaLinha(l) ?? 0), 0) / 1000;
  const custos = preenchidas.map((l) => insumoPorId(l.refs.select.value)?.custoUnitario);
  const temCusto = custos.length > 0 && custos.every((c) => c !== undefined && c !== null);
  const custoReceita = temCusto
    ? preenchidas.reduce((soma, l, i) => soma + parseFloat(l.refs.quantidade.value) * Number(custos[i]), 0)
    : null;
  const porUnidade = rendimento > 0;

  const itens = [];
  if (massaKg > 0) {
    itens.push(['Massa da receita', `${formatoNumero.format(massaKg)} kg`]);
    if (porUnidade) itens.push(['Massa por unidade', `${formatoNumero.format(Math.round((massaKg * 1000) / rendimento))} g`]);
  }
  if (custoReceita !== null) {
    itens.push(['Custo da receita', formatoReais.format(custoReceita)]);
    if (porUnidade) itens.push(['Custo por unidade', formatoReaisUnidade.format(custoReceita / rendimento)]);
    if (massaKg > 0) itens.push(['Custo por kg', formatoReais.format(custoReceita / massaKg)]);
  }
  $('resumo-ficha').replaceChildren(...itens.map(([rotulo, valor]) =>
    el('div', {}, el('dt', { textContent: rotulo }), el('dd', { textContent: valor }))));
}

function criarLinhaPasso(passo = {}) {
  const ordem = el('td', { className: 'ordem' });
  const etapa = el('input', { className: 'campo', type: 'text', maxLength: 80, value: passo.etapa ?? '', 'aria-label': 'Etapa' });
  const descricao = el('textarea', { className: 'campo', rows: 3, value: passo.descricao ?? '', 'aria-label': 'Descrição' });
  descricao.addEventListener('input', () => ajustarAltura(descricao));
  const minutos = el('input', {
    className: 'campo campo--num', type: 'number', min: '0', step: 'any', inputMode: 'decimal',
    value: passo.duracaoSegundos ? passo.duracaoSegundos / 60 : '', 'aria-label': 'Duração em minutos'
  });
  const subir = el('button', { className: 'btn btn--sec btn--icone', type: 'button', textContent: '↑' });
  const descer = el('button', { className: 'btn btn--sec btn--icone', type: 'button', textContent: '↓' });
  const remover = el('button', { className: 'btn btn--perigo btn--icone', type: 'button', textContent: '✕' });
  subir.setAttribute('aria-label', 'Subir passo');
  descer.setAttribute('aria-label', 'Descer passo');
  remover.setAttribute('aria-label', 'Remover passo');

  const linha = el('tr', {}, ordem, el('td', {}, etapa), el('td', {}, descricao), el('td', {}, minutos),
    el('td', {}, el('div', { className: 'acoes' }, subir, descer, remover)));
  linha.refs = { etapa, descricao, minutos, ordem };

  subir.addEventListener('click', () => {
    if (linha.previousElementSibling) linha.previousElementSibling.before(linha);
    numerarPassos();
  });
  descer.addEventListener('click', () => {
    if (linha.nextElementSibling) linha.nextElementSibling.after(linha);
    numerarPassos();
  });
  remover.addEventListener('click', () => {
    linha.remove();
    numerarPassos();
  });
  return linha;
}

/** A descrição cresce com o texto, para o passo inteiro ficar à vista sem rolar dentro do campo. */
function ajustarAltura(textarea) {
  textarea.style.height = 'auto';
  textarea.style.height = `${textarea.scrollHeight + 4}px`;
}

function numerarPassos() {
  [...$('passos-corpo').children].forEach((linha, i) => {
    linha.refs.ordem.textContent = String(i + 1);
  });
}

async function abrirFormulario(id = null) {
  limparMensagem();
  fichaEditandoId = id;
  try {
    const [insumos, ficha] = await Promise.all([dados.listarInsumos(), id ? dados.buscarFicha(id) : null]);
    insumosDisponiveis = [...insumos];
    // Insumo inativo de uma ficha antiga não vem em /api/insumos: mantém no seletor para não perder o vínculo.
    for (const ing of ficha?.ingredientes ?? []) {
      if (!insumosDisponiveis.some((i) => i.id === ing.insumoId)) {
        insumosDisponiveis.push({ id: ing.insumoId, nome: `${ing.insumoNome} (inativo)`, unidadeMedida: ing.unidadeMedida });
      }
    }

    $('titulo-form').textContent = ficha ? `Editar: ${ficha.nome}` : 'Nova ficha';
    $('f-nome').value = ficha?.nome ?? '';
    $('f-rendimento').value = ficha?.rendimentoPadrao ?? '';
    $('ingredientes-corpo').replaceChildren(...(ficha?.ingredientes ?? [{}]).map(criarLinhaIngrediente));
    $('passos-corpo').replaceChildren(...(ficha?.passos ?? [{}]).map(criarLinhaPasso));
    atualizarPercentuais();
    numerarPassos();
    mostrarVista('form');
    document.querySelectorAll('#passos-corpo textarea').forEach(ajustarAltura);
  } catch (erro) {
    mostrarMensagem('erro', `Não foi possível abrir a ficha: ${erro.message}`);
  }
}

/** Lê e valida o formulário. Devolve { corpo, erros }. */
function lerFormulario() {
  const erros = [];
  const nome = $('f-nome').value.trim();
  const rendimentoPadrao = Number($('f-rendimento').value);
  if (!nome) erros.push('Informe o nome da ficha.');
  if (!(rendimentoPadrao > 0) || !Number.isInteger(rendimentoPadrao)) erros.push('O rendimento padrão deve ser um número inteiro maior que zero.');

  const ingredientes = [];
  const vistos = new Set();
  [...$('ingredientes-corpo').children].forEach((linha, i) => {
    const { select, quantidade, farinha } = linha.refs;
    const rotulo = `Ingrediente ${i + 1}`;
    const q = parseFloat(quantidade.value);
    if (!select.value) erros.push(`${rotulo}: escolha o insumo.`);
    else if (vistos.has(select.value)) erros.push(`${rotulo}: o insumo já foi usado nesta ficha.`);
    vistos.add(select.value);
    if (!(q > 0)) erros.push(`${rotulo}: a quantidade base deve ser maior que zero.`);
    ingredientes.push({ insumoId: Number(select.value), quantidadeBase: q, ehFarinha: farinha.checked });
  });
  if (ingredientes.length === 0) erros.push('Adicione pelo menos um ingrediente.');
  else if (!ingredientes.some((i) => i.ehFarinha)) erros.push('Marque pelo menos um ingrediente como farinha (base dos 100%).');

  const passos = [];
  [...$('passos-corpo').children].forEach((linha, i) => {
    const { etapa, descricao, minutos } = linha.refs;
    const rotulo = `Passo ${i + 1}`;
    if (!etapa.value.trim()) erros.push(`${rotulo}: informe a etapa.`);
    if (!descricao.value.trim()) erros.push(`${rotulo}: informe a descrição.`);
    const m = minutos.value === '' ? null : parseFloat(minutos.value);
    if (m !== null && !(m >= 0)) erros.push(`${rotulo}: a duração não pode ser negativa.`);
    passos.push({
      ordem: i + 1,
      etapa: etapa.value.trim(),
      descricao: descricao.value.trim(),
      duracaoSegundos: m ? Math.round(m * 60) : null
    });
  });
  if (passos.length === 0) erros.push('Adicione pelo menos um passo do modo de preparo.');

  return { corpo: { nome, rendimentoPadrao, ingredientes, passos }, erros };
}

async function salvar() {
  limparMensagem();
  const { corpo, erros } = lerFormulario();
  if (erros.length) {
    mostrarMensagem('erro', 'Corrija antes de salvar:', erros);
    $('mensagem').scrollIntoView({ block: 'nearest' });
    return;
  }
  const botao = $('btn-salvar');
  botao.disabled = true;
  try {
    const salva = await dados.salvarFicha(fichaEditandoId, corpo);
    await carregarLista();
    mostrarVista('lista');
    mostrarMensagem('ok', `Ficha "${salva.nome}" salva.`);
  } catch (erro) {
    // A API devolve RFC 7807; erros de campo/regra (ex.: sem farinha) chegam em `detail`.
    mostrarMensagem('erro', `Não foi possível salvar: ${erro.message}`);
  } finally {
    botao.disabled = false;
  }
}

// ---------- Início ----------

const sessao = exigirLogin(['GERENTE']);
if (sessao) {
  renderizarMenu($('menu'), sessao.usuario.perfil);
  $('aviso-mock').hidden = !USAR_MOCK;
  $('btn-nova').addEventListener('click', () => abrirFormulario());
  $('btn-cancelar').addEventListener('click', () => {
    limparMensagem();
    mostrarVista('lista');
  });
  $('btn-salvar').addEventListener('click', salvar);
  $('f-rendimento').addEventListener('input', atualizarPercentuais);
  // O aviso "Corrija antes de salvar" sai assim que a pessoa mexe no formulário para corrigir.
  ['input', 'change'].forEach((evento) => $('vista-form').addEventListener(evento, () => {
    if ($('mensagem').classList.contains('msg--erro')) limparMensagem();
  }));
  $('btn-add-ingrediente').addEventListener('click', () => $('ingredientes-corpo').append(criarLinhaIngrediente()));
  $('btn-add-passo').addEventListener('click', () => {
    $('passos-corpo').append(criarLinhaPasso());
    numerarPassos();
  });
  carregarLista();
}
