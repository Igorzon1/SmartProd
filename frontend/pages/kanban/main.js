// Kanban de OPs (RF009). Dupla 3. Gerente e Funcionário.
// Colunas pelo status da OP (Q5): pendentes, em andamento e concluídas hoje. Atualiza sozinho a cada 30 s.
import { exigirLogin } from "../../js/core/auth.js";
import { renderizarMenu } from "../../js/core/menu.js";
import { apiFetch } from "../../js/core/api.js";
import { el, mostrarMensagem } from "../../js/core/dom.js";
import { formatarHorario, horaAgora } from "../../js/core/formato.js";

const ATUALIZAR_A_CADA_MS = 30_000;
const COLUNAS = ["PENDENTE", "EM_ANDAMENTO", "CONCLUIDA"];
const CHAVE_FILTRO = "smartprod.kanban.soMinhas";
const $ = (id) => document.getElementById(id);

const sessao = exigirLogin(["GERENTE", "FUNCIONARIO"]);
let ordens = [];
let carregando = false;
let concluindo = null;

if (sessao) iniciar();

function iniciar() {
  renderizarMenu($("menu"), sessao.usuario.perfil);
  $("abrir-op").hidden = sessao.usuario.perfil !== "GERENTE";
  $("so-minhas").checked = lerFiltro();
  $("so-minhas").onchange = () => {
    salvarFiltro($("so-minhas").checked);
    desenhar();
  };
  $("atualizar").onclick = carregar;
  prepararConclusao();
  carregar();
  setInterval(() => {
    if (!document.hidden && !$("concluir").open) carregar();
  }, ATUALIZAR_A_CADA_MS);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden) carregar();
  });
}

async function carregar() {
  if (carregando) return;
  carregando = true;
  $("atualizar").disabled = true;
  try {
    ordens = await apiFetch("/api/ordens-producao");
    desenhar();
    $("atualizado").textContent = `Atualizado às ${horaAgora()}.`;
  } catch (erro) {
    mostrarMensagem($("mensagem"), erro.message, true);
  } finally {
    carregando = false;
    $("atualizar").disabled = false;
  }
}

function desenhar() {
  const soMinhas = $("so-minhas").checked;
  const visiveis = ordens.filter((op) => !soMinhas || op.responsavel?.id === sessao.usuario.id);
  for (const status of COLUNAS) {
    const lista = visiveis.filter((op) => op.status === status);
    $(`n-${status}`).textContent = String(lista.length);
    $(`c-${status}`).replaceChildren(
      ...(lista.length ? lista.map(cartao) : [el("p", { className: "vazio", textContent: "Nenhuma OP aqui." })]),
    );
  }
}

function cartao(op) {
  const minha = op.responsavel?.id === sessao.usuario.id;
  const atrasada = op.status !== "CONCLUIDA" && new Date(op.horarioEntrega) < new Date();
  const classes = ["op", minha && "op--minha", atrasada && "op--atrasada"].filter(Boolean).join(" ");

  const acoes = el("div", { className: "op__acoes" });
  if (op.status === "PENDENTE") {
    const iniciar = el("button", { type: "button", className: "btn", textContent: "Iniciar", "aria-label": `Iniciar OP ${op.id}` });
    iniciar.onclick = () => iniciarOp(op, iniciar);
    acoes.append(iniciar);
  } else if (op.status === "EM_ANDAMENTO") {
    const concluir = el("button", { type: "button", className: "btn btn--secundario", textContent: "Concluir", "aria-label": `Concluir OP ${op.id}` });
    concluir.onclick = () => abrirConclusao(op);
    acoes.append(
      el("a", { className: "btn", href: `../execucao/index.html?id=${op.id}`, textContent: "Passo a passo", "aria-label": `Passo a passo da OP ${op.id}` }),
      concluir,
    );
  }

  return el(
    "article",
    { className: classes, "aria-label": `OP ${op.id}: ${op.fichaNome}` },
    el("h3", { textContent: op.fichaNome }),
    el("p", { className: "op__linha", textContent: `OP #${op.id} · ${op.quantidadeUnidades} un.` }),
    el("p", {
      className: "op__linha op__entrega",
      textContent: `Entrega: ${formatarHorario(op.horarioEntrega)}${atrasada ? " (atrasada)" : ""}`,
    }),
    el("p", { className: "op__linha", textContent: `Responsável: ${minha ? "você" : op.responsavel?.nome ?? "—"}` }),
    op.status === "CONCLUIDA"
      ? el("p", { className: "op__linha", textContent: `Produzido: ${op.quantidadeProduzida} de ${op.quantidadeUnidades} un.` })
      : null,
    acoes.childElementCount ? acoes : null,
  );
}

async function iniciarOp(op, botao) {
  botao.disabled = true;
  try {
    await apiFetch(`/api/ordens-producao/${op.id}/iniciar`, { metodo: "PATCH" });
    mostrarMensagem($("mensagem"), `OP #${op.id} (${op.fichaNome}) iniciada.`);
    await carregar();
  } catch (erro) {
    mostrarMensagem($("mensagem"), erro.message, true);
    botao.disabled = false;
  }
}

// ---------- Concluir: a quantidade produzida já vem preenchida com a prevista (Q13)

function prepararConclusao() {
  const dialogo = $("concluir");
  const form = $("form-concluir");
  $("cancelar-concluir").onclick = () => dialogo.close();
  form.onsubmit = async (evento) => {
    evento.preventDefault();
    const quantidade = Number(form.elements.quantidade.value);
    if (!Number.isInteger(quantidade) || quantidade < 0) {
      $("erro-concluir").textContent = "Informe um número inteiro de unidades (zero ou mais).";
      return;
    }
    $("confirmar-concluir").disabled = true;
    try {
      await apiFetch(`/api/ordens-producao/${concluindo.id}/concluir`, {
        metodo: "PATCH",
        corpo: { quantidadeProduzida: quantidade },
      });
      dialogo.close();
      mostrarMensagem($("mensagem"), `OP #${concluindo.id} concluída com ${quantidade} unidades.`);
      await carregar();
    } catch (erro) {
      $("erro-concluir").textContent = erro.message;
    } finally {
      $("confirmar-concluir").disabled = false;
    }
  };
}

function abrirConclusao(op) {
  concluindo = op;
  const form = $("form-concluir");
  form.elements.quantidade.value = op.quantidadeUnidades;
  $("concluir-texto").textContent = `${op.fichaNome} · OP #${op.id} · prevista: ${op.quantidadeUnidades} un.`;
  $("erro-concluir").textContent = "";
  $("concluir").showModal();
  form.elements.quantidade.select();
}

function lerFiltro() {
  try {
    return localStorage.getItem(CHAVE_FILTRO) === "true";
  } catch {
    return false;
  }
}

function salvarFiltro(valor) {
  try {
    localStorage.setItem(CHAVE_FILTRO, String(valor));
  } catch {
    // sem localStorage o filtro vale só até recarregar
  }
}
