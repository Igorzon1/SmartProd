// Abrir OP (RF008) e cancelar OP pendente aberta por engano. Dupla 3. Só Gerente.
import { exigirLogin } from "../../js/core/auth.js";
import { renderizarMenu } from "../../js/core/menu.js";
import { apiFetch } from "../../js/core/api.js";
import { el, mostrarMensagem } from "../../js/core/dom.js";
import { formatarHorario, hojeSaoPaulo, instanteDeSaoPaulo, diaSaoPaulo } from "../../js/core/formato.js";
import { icone } from "../../js/core/icones.js";

const SITUACOES = { PENDENTE: "Pendente", EM_ANDAMENTO: "Em andamento", CONCLUIDA: "Concluída" };
const $ = (id) => document.getElementById(id);

const sessao = exigirLogin(["GERENTE"]);
const form = $("form-op");
let salvando = false;
/** OP escolhida para cancelar, enquanto a confirmação está aberta. */
let alvo = null;
/** OP aberta agora há pouco: a linha dela fica destacada na lista enquanto a página estiver aberta. */
let opNova = null;

if (sessao) iniciar();

async function iniciar() {
  renderizarMenu($("menu"), sessao.usuario.perfil);
  $("ir-kanban").prepend(icone("kanban"));
  $("confirmacao-kanban").prepend(icone("kanban"));
  $("confirmacao-lista").onclick = irParaLinhaNova;
  form.elements.data.value = hojeSaoPaulo();
  form.elements.data.min = hojeSaoPaulo();
  form.elements.hora.value = proximaHoraCheia();
  $("dia").value = hojeSaoPaulo();
  $("dia").onchange = carregarOps;
  $("recarregar").onclick = carregarOps;
  form.onsubmit = abrirOp;
  $("voltar").onclick = () => $("cancelamento").close();
  $("confirmar-cancelamento").onclick = cancelarOp;
  await Promise.all([carregarOpcoes(), carregarOps()]);
  preencherPelaCalculadora();
}

/** A dica da quantidade mostra o rendimento de uma receita do produto escolhido. */
function dicaDaQuantidade() {
  const rendimento = form.elements.fichaTecnicaId.selectedOptions[0]?.dataset.rendimento;
  form.elements.quantidadeUnidades.placeholder = rendimento ? `Ex.: ${rendimento}` : "Escolha o produto";
  $("dica-quantidade").textContent = rendimento ? `Uma receita rende ${rendimento} unidades.` : "";
}

/** Vindo da calculadora (?fichaId=3&quantidade=270): o produto e a quantidade já chegam preenchidos. */
function preencherPelaCalculadora() {
  const parametros = new URLSearchParams(location.search);
  const fichaId = parametros.get("fichaId");
  const quantidade = Number(parametros.get("quantidade"));
  const select = form.elements.fichaTecnicaId;
  if (fichaId && [...select.options].some((opcao) => opcao.value === fichaId)) select.value = fichaId;
  dicaDaQuantidade();
  if (Number.isInteger(quantidade) && quantidade > 0) form.elements.quantidadeUnidades.value = String(quantidade);
  if (fichaId || quantidade) form.elements.responsavelId.focus();
}

/** Fichas e usuários ativos para os campos do formulário. */
async function carregarOpcoes() {
  try {
    const [fichas, usuarios] = await Promise.all([
      apiFetch("/api/fichas-tecnicas"),
      apiFetch("/api/usuarios?ativo=true"),
    ]);
    preencher(form.elements.fichaTecnicaId, fichas, (f) => `${f.nome} (rende ${f.rendimentoPadrao} un.)`,
      "Cadastre uma ficha técnica antes de abrir uma OP.");
    for (const opcao of form.elements.fichaTecnicaId.options) {
      const ficha = fichas.find((f) => String(f.id) === opcao.value);
      if (ficha) opcao.dataset.rendimento = String(ficha.rendimentoPadrao);
    }
    form.elements.fichaTecnicaId.onchange = dicaDaQuantidade;
    preencher(form.elements.responsavelId, usuarios,
      (u) => `${u.nome} (${u.perfil === "GERENTE" ? "Gerente" : "Funcionário"})`, "Nenhum usuário ativo.");
  } catch (erro) {
    mostrarMensagem($("mensagem"), erro.message, true);
  }
}

function preencher(select, itens, rotulo, textoVazio) {
  const opcoes = itens.map((item) => el("option", { value: String(item.id), textContent: rotulo(item) }));
  select.replaceChildren(el("option", { value: "", textContent: itens.length ? "Selecione…" : textoVazio }), ...opcoes);
}

async function abrirOp(evento) {
  evento.preventDefault();
  if (salvando) return;
  const quantidade = Number(form.elements.quantidadeUnidades.value);
  if (!Number.isInteger(quantidade) || quantidade < 1) {
    $("erro-form").textContent = "Informe um número inteiro de unidades, a partir de 1.";
    return;
  }
  const horarioEntrega = instanteDeSaoPaulo(form.elements.data.value, form.elements.hora.value);
  // A API recusa entrega no passado; avisar aqui poupa a ida ao servidor (o minuto atual ainda vale).
  const inicioDoMinuto = new Date();
  inicioDoMinuto.setSeconds(0, 0);
  if (new Date(horarioEntrega) < inicioDoMinuto) {
    $("erro-form").textContent = "O horário de entrega já passou. Escolha uma data e hora a partir de agora.";
    return;
  }
  const corpo = {
    fichaTecnicaId: Number(form.elements.fichaTecnicaId.value),
    quantidadeUnidades: quantidade,
    horarioEntrega,
    responsavelId: Number(form.elements.responsavelId.value),
  };

  salvando = true;
  $("abrir").disabled = true;
  $("erro-form").textContent = "";
  $("confirmacao").hidden = true;
  mostrarMensagem($("mensagem"), "");
  try {
    const op = await apiFetch("/api/ordens-producao", { metodo: "POST", corpo });
    opNova = op;
    form.elements.quantidadeUnidades.value = "";
    $("dia").value = diaSaoPaulo(op.horarioEntrega);
    await carregarOps();
    mostrarConfirmacao(op);
  } catch (erro) {
    $("erro-form").textContent = erro.message;
  } finally {
    salvando = false;
    $("abrir").disabled = false;
  }
}

/** Caixa verde logo abaixo do botão, com o caminho para ver a OP no Kanban ou na lista. */
function mostrarConfirmacao(op) {
  $("confirmacao-titulo").textContent = `OP #${op.id} aberta`;
  $("confirmacao-texto").textContent = `${op.fichaNome}, ${op.quantidadeUnidades} un., entrega `
    + `${formatarHorario(op.horarioEntrega)}, responsável ${op.responsavel.nome}. `
    + "Ela entrou na coluna Pendentes do Kanban e está destacada na lista abaixo.";
  $("confirmacao-kanban").href = linkKanban(op.id);
  const caixa = $("confirmacao");
  caixa.hidden = false;
  // Reinicia a animação de entrada quando outra OP é aberta em seguida.
  caixa.classList.remove("confirmacao--entrando");
  void caixa.offsetWidth;
  caixa.classList.add("confirmacao--entrando");
  caixa.scrollIntoView({ behavior: rolagem(), block: "nearest" });
}

async function irParaLinhaNova() {
  if (!opNova) return;
  // Se a pessoa trocou o dia da lista depois de abrir a OP, volta para o dia da entrega dela.
  if (!document.querySelector(".linha--nova")) {
    $("dia").value = diaSaoPaulo(opNova.horarioEntrega);
    await carregarOps();
  }
  const linhaNova = document.querySelector(".linha--nova");
  if (!linhaNova) return;
  linhaNova.scrollIntoView({ behavior: rolagem(), block: "center" });
  linhaNova.classList.remove("linha--piscando");
  void linhaNova.offsetWidth;
  linhaNova.classList.add("linha--piscando");
}

/** Kanban já rolado até a OP e com o cartão dela destacado. */
function linkKanban(id) {
  return `../kanban/index.html?op=${id}`;
}

function rolagem() {
  return matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth";
}

async function carregarOps() {
  const dia = $("dia").value || hojeSaoPaulo();
  $("contagem").textContent = "Carregando…";
  try {
    const ops = await apiFetch(`/api/ordens-producao?data=${dia}`);
    $("contagem").textContent = ops.length ? `${ops.length} OP(s) com entrega neste dia.` : "Nenhuma OP com entrega neste dia.";
    $("linhas").replaceChildren(...ops.map(linha));
  } catch (erro) {
    $("contagem").textContent = "Não foi possível carregar as OPs.";
    mostrarMensagem($("mensagem"), erro.message, true);
  }
}

function linha(op) {
  const situacao = op.status === "CONCLUIDA"
    ? `${SITUACOES.CONCLUIDA} (${op.quantidadeProduzida} de ${op.quantidadeUnidades})`
    : SITUACOES[op.status];
  const nova = op.id === opNova?.id;
  const celulas = [op.fichaNome, `${op.quantidadeUnidades} un.`, formatarHorario(op.horarioEntrega),
    op.responsavel?.nome ?? "—"].map((texto) => el("td", { textContent: texto }));
  const acoes = [];
  // O Kanban mostra as pendentes e as em andamento (as concluídas só no dia da conclusão).
  if (op.status !== "CONCLUIDA") acoes.push(botaoKanban(op));
  if (op.status === "PENDENTE") acoes.push(botaoCancelar(op));
  return el("tr", { className: nova ? "linha--nova" : "" },
    el("td", {}, `#${op.id}`, nova ? el("span", { className: "etiqueta-nova", textContent: "Nova" }) : null),
    ...celulas,
    el("td", {}, el("span", { className: `situacao situacao--${op.status}`, textContent: situacao })),
    el("td", {}, el("div", { className: "acoes" }, ...acoes)));
}

function botaoKanban(op) {
  const link = el("a", { className: "btn btn--secundario btn--com-icone", href: linkKanban(op.id),
    textContent: "Ver no Kanban", "aria-label": `Ver a OP ${op.id} no Kanban` });
  link.prepend(icone("kanban"));
  return link;
}

/** Só OP pendente pode ser cancelada; a que já começou é concluída com a quantidade real. */
function botaoCancelar(op) {
  const botao = el("button", { type: "button", className: "btn btn--secundario", textContent: "Cancelar",
    "aria-label": `Cancelar OP ${op.id}` });
  botao.onclick = () => {
    alvo = op;
    $("cancelar-texto").textContent = `OP #${op.id}: ${op.fichaNome}, ${op.quantidadeUnidades} un., entrega `
      + `${formatarHorario(op.horarioEntrega)}. Ela sai do Kanban e do rendimento do dia. Não dá para desfazer.`;
    $("erro-cancelar").textContent = "";
    $("cancelamento").showModal();
  };
  return botao;
}

async function cancelarOp() {
  if (!alvo) return;
  const botao = $("confirmar-cancelamento");
  botao.disabled = true;
  $("erro-cancelar").textContent = "";
  try {
    await apiFetch(`/api/ordens-producao/${alvo.id}`, { metodo: "DELETE" });
    $("cancelamento").close();
    mostrarMensagem($("mensagem"), `OP #${alvo.id} (${alvo.fichaNome}) cancelada.`);
    if (alvo.id === opNova?.id) {
      opNova = null;
      $("confirmacao").hidden = true;
    }
    alvo = null;
    await carregarOps();
  } catch (erro) {
    // Ex.: alguém iniciou a OP enquanto a confirmação estava aberta (409) ou ela já foi cancelada (404):
    // mostra o motivo e recarrega a lista, para a linha refletir a situação real.
    $("erro-cancelar").textContent = erro.message;
    if (erro.status === 409 || erro.status === 404) await carregarOps();
  } finally {
    botao.disabled = false;
  }
}

/** Próxima hora cheia em São Paulo ("15:00"); depois das 23h, fica em 23:59. */
function proximaHoraCheia() {
  const hora = Number(new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", hour: "2-digit", hourCycle: "h23" })
    .format(new Date()));
  return hora >= 23 ? "23:59" : `${String(hora + 1).padStart(2, "0")}:00`;
}
