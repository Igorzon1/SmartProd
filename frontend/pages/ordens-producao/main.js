// Abrir OP (RF008). Dupla 3. Só Gerente.
import { exigirLogin } from "../../js/core/auth.js";
import { renderizarMenu } from "../../js/core/menu.js";
import { apiFetch } from "../../js/core/api.js";
import { el, mostrarMensagem } from "../../js/core/dom.js";
import { formatarHorario, hojeSaoPaulo, instanteDeSaoPaulo, diaSaoPaulo } from "../../js/core/formato.js";

const SITUACOES = { PENDENTE: "Pendente", EM_ANDAMENTO: "Em andamento", CONCLUIDA: "Concluída" };
const $ = (id) => document.getElementById(id);

const sessao = exigirLogin(["GERENTE"]);
const form = $("form-op");
let salvando = false;

if (sessao) iniciar();

async function iniciar() {
  renderizarMenu($("menu"), sessao.usuario.perfil);
  form.elements.data.value = hojeSaoPaulo();
  form.elements.hora.value = proximaHoraCheia();
  $("dia").value = hojeSaoPaulo();
  $("dia").onchange = carregarOps;
  $("recarregar").onclick = carregarOps;
  form.onsubmit = abrirOp;
  await Promise.all([carregarOpcoes(), carregarOps()]);
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
  const corpo = {
    fichaTecnicaId: Number(form.elements.fichaTecnicaId.value),
    quantidadeUnidades: quantidade,
    horarioEntrega: instanteDeSaoPaulo(form.elements.data.value, form.elements.hora.value),
    responsavelId: Number(form.elements.responsavelId.value),
  };

  salvando = true;
  $("abrir").disabled = true;
  $("erro-form").textContent = "";
  try {
    const op = await apiFetch("/api/ordens-producao", { metodo: "POST", corpo });
    mostrarMensagem($("mensagem"),
      `OP #${op.id} aberta: ${op.fichaNome}, ${op.quantidadeUnidades} un., entrega ${formatarHorario(op.horarioEntrega)}, `
        + `responsável ${op.responsavel.nome}.`);
    form.elements.quantidadeUnidades.value = "";
    $("dia").value = diaSaoPaulo(op.horarioEntrega);
    await carregarOps();
  } catch (erro) {
    $("erro-form").textContent = erro.message;
  } finally {
    salvando = false;
    $("abrir").disabled = false;
  }
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
  const celulas = [`#${op.id}`, op.fichaNome, `${op.quantidadeUnidades} un.`, formatarHorario(op.horarioEntrega),
    op.responsavel?.nome ?? "—"].map((texto) => el("td", { textContent: texto }));
  return el("tr", {}, ...celulas,
    el("td", {}, el("span", { className: `situacao situacao--${op.status}`, textContent: situacao })));
}

/** Próxima hora cheia em São Paulo ("15:00"); depois das 23h, fica em 23:59. */
function proximaHoraCheia() {
  const hora = Number(new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", hour: "2-digit", hourCycle: "h23" })
    .format(new Date()));
  return hora >= 23 ? "23:59" : `${String(hora + 1).padStart(2, "0")}:00`;
}
