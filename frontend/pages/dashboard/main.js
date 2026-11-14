// Dashboard do Gerente (RF012 a RF015). Dupla 2. Só GERENTE (RNF-S002).
import { exigirLogin } from '../../js/core/auth.js';
import { renderizarMenu } from '../../js/core/menu.js';
import { apiFetch } from '../../js/core/api.js';

/**
 * true = dados falsos de mock/. Troque para false quando D2-BE-04 a 07 e a dupla 3 estiverem no ar.
 * Em modo falso, abra a página com ?mock=vazio para ver os estados sem dados.
 */
const USAR_MOCK = true;

const $ = (id) => document.getElementById(id);
const numero = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 });
const reais = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dataBR = (iso) => iso.split('-').reverse().join('/');

function el(tag, props = {}, ...filhos) {
  const elemento = document.createElement(tag);
  for (const [chave, valor] of Object.entries(props)) elemento[chave] = valor;
  elemento.append(...filhos);
  return elemento;
}

// ---------- Datas (a interface usa America/Sao_Paulo; a API trabalha em UTC) ----------

function hojeSaoPaulo() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date()); // AAAA-MM-DD
}

function somarDias(iso, dias) {
  const [a, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(a, m - 1, d + dias)).toISOString().slice(0, 10);
}

// ---------- Acesso a dados (API real ou mock) ----------

let mockCache = null;
async function lerMock() {
  if (!mockCache) {
    const arquivo = new URLSearchParams(location.search).get('mock') === 'vazio' ? 'dashboard-vazio.json' : 'dashboard.json';
    mockCache = await fetch(new URL(`mock/${arquivo}`, import.meta.url)).then((r) => r.json());
  }
  return mockCache;
}

/**
 * Contrato do docs/09. ATENÇÃO: o formato de `de`/`ate` não está definido ("de=...&ate=...").
 * Assumido AAAA-MM-DD (igual ao `data` do rendimento). Confirmar com o backend da dupla 2.
 */
const dados = {
  async rendimento(dia) {
    return USAR_MOCK ? (await lerMock()).rendimento : apiFetch(`/api/dashboard/rendimento?data=${dia}`);
  },
  async desperdicio(de, ate) {
    return USAR_MOCK ? (await lerMock()).desperdicio : apiFetch(`/api/dashboard/desperdicio?de=${de}&ate=${ate}`);
  },
  async ranking(de, ate) {
    return USAR_MOCK ? (await lerMock()).ranking : apiFetch(`/api/dashboard/ranking-desperdicio?de=${de}&ate=${ate}`);
  },
  async statusOps() {
    return USAR_MOCK ? (await lerMock()).statusOps : apiFetch('/api/dashboard/status-ops');
  }
};

// ---------- Renderização dos cartões ----------

function barra(percentual) {
  const largura = Math.max(0, Math.min(100, percentual)); // o valor real aparece no texto; a barra para em 100%
  const preenchimento = el('div', { className: 'barra__preenchimento' });
  preenchimento.style.width = `${largura}%`;
  return el('div', { className: 'barra', 'aria-hidden': 'true' }, preenchimento);
}

function mensagemVazia(texto) {
  return el('p', { className: 'vazio', textContent: texto });
}

function renderRendimento(r) {
  if (!r || !(r.previsto > 0) || r.rendimentoPercentual == null) {
    return [mensagemVazia('Nenhuma OP prevista para este dia.')];
  }
  return [
    el('p', { className: 'destaque', textContent: `${numero.format(r.rendimentoPercentual)}%` }),
    el('p', { className: 'legenda', textContent: `${numero.format(r.produzido)} produzidos de ${numero.format(r.previsto)} previstos` }),
    barra(r.rendimentoPercentual)
  ];
}

function renderDesperdicio(d) {
  return [
    el('div', { className: 'duplo' },
      el('div', {}, el('p', { className: 'destaque', textContent: `${numero.format(d.totalKg)} kg` }), el('p', { className: 'legenda', textContent: 'perdidos' })),
      el('div', {}, el('p', { className: 'destaque', textContent: reais.format(d.totalReais) }), el('p', { className: 'legenda', textContent: 'em valor' })))
  ];
}

function renderRanking(lista) {
  const top = (lista ?? []).slice(0, 3);
  if (top.length === 0) return [mensagemVazia('Nenhuma perda registrada no período.')];
  const maior = Math.max(...top.map((p) => p.totalKg));
  return [
    el('ol', { className: 'ranking' },
      ...top.map((p, i) =>
        el('li', {},
          el('div', { className: 'ranking__topo' },
            el('span', { textContent: `${i + 1}. ${p.produto}` }),
            el('span', { className: 'ranking__valor' }, `${numero.format(p.totalKg)} kg`, el('small', { textContent: reais.format(p.totalReais) }))),
          barra(maior > 0 ? (p.totalKg / maior) * 100 : 0))))
  ];
}

function renderStatus(s) {
  const itens = [
    { rotulo: 'Pendentes', valor: s.pendentes, classe: 'pendente' },
    { rotulo: 'Em andamento', valor: s.emAndamento, classe: 'andamento' },
    { rotulo: 'Concluídas', valor: s.concluidas, classe: 'concluida' }
  ];
  const total = itens.reduce((soma, i) => soma + i.valor, 0);
  if (total === 0) return [mensagemVazia('Nenhuma ordem de produção cadastrada.')];
  const segmentos = itens.map((i) => {
    const parte = el('span', { className: `marca--${i.classe}` });
    parte.style.width = `${(i.valor / total) * 100}%`;
    return parte;
  });
  return [
    el('div', { className: 'status-barra', 'aria-hidden': 'true' }, ...segmentos),
    el('ul', { className: 'status-lista' },
      ...itens.map((i) => el('li', {}, el('span', { className: `marca marca--${i.classe}`, 'aria-hidden': 'true' }), el('strong', { textContent: String(i.valor) }), i.rotulo)))
  ];
}

/** Cada cartão carrega e falha sozinho: um erro em um indicador não esconde os outros. */
function preencher(idCartao, resultado, renderizador) {
  const area = $(idCartao);
  if (resultado.status === 'rejected') {
    const erro = resultado.reason;
    const texto = erro?.status === 403 ? 'Sem permissão para ver este indicador.' : `Não foi possível carregar: ${erro?.message ?? 'erro desconhecido'}`;
    area.replaceChildren(el('p', { className: 'erro-card', textContent: texto }));
    return;
  }
  area.replaceChildren(...renderizador(resultado.value));
}

// ---------- Ações ----------

function mostrarErro(texto) {
  $('mensagem').textContent = texto;
  $('mensagem').hidden = false;
}

async function atualizar(evento) {
  evento?.preventDefault();
  $('mensagem').hidden = true;

  const dia = $('f-dia').value;
  const de = $('f-de').value;
  const ate = $('f-ate').value;
  if (!dia || !de || !ate) return mostrarErro('Preencha o dia do rendimento e o período do desperdício.');
  if (de > ate) return mostrarErro('No período do desperdício, a data inicial não pode ser depois da final.');

  const periodo = `${dataBR(de)} a ${dataBR(ate)}`;
  $('p-rendimento').textContent = dataBR(dia);
  $('p-desperdicio').textContent = periodo;
  $('p-ranking').textContent = periodo;
  ['c-rendimento', 'c-desperdicio', 'c-ranking', 'c-status'].forEach((id) =>
    $(id).replaceChildren(el('p', { className: 'vazio', textContent: 'Carregando…' })));

  const botao = $('btn-atualizar');
  botao.disabled = true;
  try {
    const [rend, desp, rank, status] = await Promise.allSettled([
      dados.rendimento(dia), dados.desperdicio(de, ate), dados.ranking(de, ate), dados.statusOps()
    ]);
    preencher('c-rendimento', rend, renderRendimento);
    preencher('c-desperdicio', desp, renderDesperdicio);
    preencher('c-ranking', rank, renderRanking);
    preencher('c-status', status, renderStatus);
  } finally {
    botao.disabled = false;
  }
}

// ---------- Início ----------

const sessao = exigirLogin(['GERENTE']);
if (sessao) {
  renderizarMenu($('menu'), sessao.usuario.perfil);
  $('aviso-mock').hidden = !USAR_MOCK;
  const hoje = hojeSaoPaulo();
  $('f-dia').value = hoje;
  $('f-ate').value = hoje;
  $('f-de').value = somarDias(hoje, -6); // padrão: últimos 7 dias
  $('filtros').addEventListener('submit', atualizar);
  atualizar();
}
