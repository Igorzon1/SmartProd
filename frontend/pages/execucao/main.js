// Passo a passo da OP (RF010) e registro de perda (RF011). Dupla 3. Gerente e Funcionário.
// O passo atual, o temporizador e os ingredientes marcados ficam no localStorage (RNF-C002):
// recarregar a página ou trocar de tela não perde o progresso.
import { exigirLogin } from "../../js/core/auth.js";
import { renderizarMenu } from "../../js/core/menu.js";
import { apiFetch } from "../../js/core/api.js";
import { el, mostrarMensagem } from "../../js/core/dom.js";
import { formatarHorario, formatarNumero, formatarQuantidadeLegivel } from "../../js/core/formato.js";

const SITUACOES = { PENDENTE: "Pendente", EM_ANDAMENTO: "Em andamento", CONCLUIDA: "Concluída" };
const $ = (id) => document.getElementById(id);

const sessao = exigirLogin(["GERENTE", "FUNCIONARIO"]);
const opId = Number(new URLSearchParams(location.search).get("id")) || null;

/** Resposta de GET /api/ordens-producao/{id}/execucao. */
let execucao = null;
/**
 * { passo, separados: [insumoId], timers: { [indiceDoPasso]: { restanteMs, rodandoDesde, avisado } } }
 * Cada passo tem o próprio temporizador: dá para ir ao próximo passo com a fermentação ainda correndo.
 */
let progresso = null;
let audio = null;
/** Massa crua de uma unidade, em kg (soma dos ingredientes pesáveis ÷ unidades do lote), para sugerir o peso da perda. */
let kgPorUnidade = null;
/** O peso da perda foi preenchido pela sugestão (e não digitado): pode ser trocado quando as unidades mudam. */
let pesoSugerido = false;

const KG_POR_UNIDADE_DE_MEDIDA = { G: 0.001, ML: 0.001, KG: 1, L: 1 };

if (sessao) {
  renderizarMenu($("menu"), sessao.usuario.perfil);
  if (opId) abrirOp();
  else listarEmAndamento();
}

// ---------- Sem OP escolhida

async function listarEmAndamento() {
  $("escolha").hidden = false;
  try {
    const ops = await apiFetch("/api/ordens-producao?status=EM_ANDAMENTO");
    const cartoes = ops.map((op) =>
      el("a", { className: "card op-escolha", href: `?id=${op.id}` },
        el("strong", { textContent: op.fichaNome }),
        el("span", { textContent: `OP #${op.id} · ${op.quantidadeUnidades} un. · entrega ${formatarHorario(op.horarioEntrega)}` }),
        el("span", { textContent: `Responsável: ${op.responsavel?.id === sessao.usuario.id ? "você" : op.responsavel?.nome}` }),
        // O cartão inteiro é o link; o "botão" mostra que dá para tocar.
        el("span", { className: "btn btn--grande op-escolha__abrir", textContent: "Abrir passo a passo →" })));
    $("lista-ops").replaceChildren(
      ...(cartoes.length ? cartoes : [el("p", { className: "hint", textContent: "Nenhuma OP em andamento. Inicie uma OP no Kanban." })]));
  } catch (erro) {
    mostrarMensagem($("mensagem"), erro.message, true);
  }
}

// ---------- OP escolhida

async function abrirOp() {
  $("execucao").hidden = false;
  try {
    execucao = await apiFetch(`/api/ordens-producao/${opId}/execucao`);
  } catch (erro) {
    $("titulo").textContent = "OP não encontrada";
    mostrarMensagem($("mensagem"), erro.message, true);
    return;
  }
  const salvo = lerProgresso();
  progresso = { passo: 0, separados: [], timers: {}, ...salvo };
  progresso.passo = Math.min(progresso.passo, Math.max(0, execucao.passos.length - 1));

  kgPorUnidade = calcularKgPorUnidade();
  prepararBotoes();
  desenharCabecalho();
  desenharIngredientes();
  desenharPasso();
  carregarPerdas();
  setInterval(atualizarTemporizador, 500);
}

function emAndamento() {
  return execucao.status === "EM_ANDAMENTO";
}

function desenharCabecalho() {
  document.title = `${execucao.fichaNome} — Execução — SmartProd`;
  $("titulo").textContent = execucao.fichaNome;
  $("situacao").textContent = SITUACOES[execucao.status];
  $("situacao").className = `situacao situacao--${execucao.status}`;
  $("resumo").textContent = `OP #${execucao.ordemProducaoId} · lote de ${execucao.quantidadeUnidades} unidades`;
  $("iniciar-op").hidden = execucao.status !== "PENDENTE";
  $("registrar-perda").hidden = !emAndamento();
  const aviso = execucao.status === "PENDENTE"
    ? "Esta OP ainda não foi iniciada. Inicie para registrar perdas e concluir."
    : execucao.status === "CONCLUIDA" ? "Esta OP já foi concluída. O passo a passo fica só para consulta." : "";
  $("aviso").textContent = aviso;
  $("aviso").hidden = !aviso;
}

function desenharIngredientes() {
  const itens = execucao.ingredientes.map((ingrediente) => {
    const caixa = el("input", { type: "checkbox", checked: progresso.separados.includes(ingrediente.insumoId) });
    caixa.onchange = () => {
      progresso.separados = caixa.checked
        ? [...progresso.separados, ingrediente.insumoId]
        : progresso.separados.filter((id) => id !== ingrediente.insumoId);
      salvarProgresso();
    };
    return el("li", {}, el("label", {}, caixa,
      el("span", { className: "quantidade", textContent: formatarQuantidadeLegivel(ingrediente.quantidade, ingrediente.unidadeMedida) }),
      el("span", { textContent: ingrediente.nome })));
  });
  $("ingredientes").replaceChildren(
    ...(itens.length ? itens : [el("li", { className: "hint", textContent: "A ficha não tem ingredientes cadastrados." })]));
}

// ---------- Passo a passo

function desenharPasso() {
  const passos = execucao.passos;
  if (!passos.length) {
    $("passo-numero").textContent = "";
    $("passo-etapa").textContent = "Sem passos cadastrados";
    $("passo-descricao").textContent = "Esta ficha técnica ainda não tem modo de preparo.";
    $("temporizador").hidden = true;
    $("anterior").hidden = $("proximo").hidden = true;
    $("finalizar").hidden = !emAndamento();
    return;
  }
  const indice = progresso.passo;
  const passo = passos[indice];
  const ultimo = indice === passos.length - 1;

  $("passo-numero").textContent = `Passo ${indice + 1} de ${passos.length}`;
  $("passo-etapa").textContent = passo.etapa;
  $("passo-descricao").textContent = passo.descricao;
  $("anterior").disabled = indice === 0;
  $("proximo").hidden = ultimo;
  $("finalizar").hidden = !ultimo || !emAndamento();
  $("progresso").replaceChildren(...passos.map((_, i) =>
    el("span", { className: i < indice ? "feito" : i === indice ? "atual" : "" })));

  // OP concluída: o passo a passo fica só para consulta, sem temporizador.
  $("temporizador").hidden = !passo.duracaoSegundos || execucao.status === "CONCLUIDA";
  atualizarTemporizador();
}

function irParaPasso(indice) {
  progresso.passo = indice;
  salvarProgresso();
  desenharPasso();
  // No tablet o botão "Próximo" fica embaixo: se o começo do passo saiu da tela, rola até ele.
  const cartao = document.querySelector(".passo");
  if (cartao.getBoundingClientRect().top < 0) cartao.scrollIntoView({ block: "start" });
}

// ---------- Temporizador (continua certo mesmo se a página recarregar)

function duracaoMs(indice) {
  return (execucao.passos[indice]?.duracaoSegundos ?? 0) * 1000;
}

/** Temporizador do passo; nasce parado, com a duração cheia. */
function timerDoPasso(indice) {
  progresso.timers[indice] ??= { restanteMs: duracaoMs(indice), rodandoDesde: null, avisado: false };
  return progresso.timers[indice];
}

function restanteMs(timer) {
  return timer.rodandoDesde ? Math.max(0, timer.restanteMs - (Date.now() - timer.rodandoDesde)) : timer.restanteMs;
}

/** Roda a cada meio segundo: avisa qualquer passo cujo tempo acabou e redesenha o do passo atual. */
function atualizarTemporizador() {
  if (!progresso) return;
  for (const [indice, timer] of Object.entries(progresso.timers)) {
    if (timer.rodandoDesde && restanteMs(timer) === 0 && !timer.avisado) {
      timer.avisado = true;
      timer.rodandoDesde = null;
      timer.restanteMs = 0;
      salvarProgresso();
      tocarAlarme();
      mostrarAlarme(Number(indice));
    }
  }
  if ($("temporizador").hidden) return;

  const timer = timerDoPasso(progresso.passo);
  const restante = restanteMs(timer);
  const segundos = Math.ceil(restante / 1000);
  $("tempo").textContent = `${String(Math.floor(segundos / 60)).padStart(2, "0")}:${String(segundos % 60).padStart(2, "0")}`;
  const rodando = Boolean(timer.rodandoDesde) && restante > 0;
  $("timer-iniciar").hidden = rodando;
  $("timer-iniciar").textContent = restante > 0 && restante < duracaoMs(progresso.passo) ? "Continuar" : "Iniciar tempo";
  $("timer-iniciar").disabled = restante === 0;
  $("timer-pausar").hidden = !rodando;
  $("temporizador").classList.toggle("temporizador--alarme", timer.avisado);
  $("tempo-aviso").textContent = timer.avisado ? "Tempo esgotado!" : "";
}

/** Alarme fixo no topo da tela: avisa mesmo com a página rolada. Se for de outro passo, leva até ele. */
function mostrarAlarme(indice) {
  const passo = execucao.passos[indice];
  const outroPasso = indice !== progresso.passo;
  $("alarme-texto").textContent = `Tempo esgotado no passo ${indice + 1} (${passo.etapa})!`;
  $("alarme-ver").hidden = !outroPasso;
  $("alarme-ver").textContent = `Ver passo ${indice + 1}`;
  $("alarme-ver").onclick = () => {
    fecharAlarme();
    irParaPasso(indice);
  };
  $("alarme").hidden = false;
}

function fecharAlarme() {
  $("alarme").hidden = true;
}

function iniciarTimer() {
  prepararSom();
  timerDoPasso(progresso.passo).rodandoDesde = Date.now();
  salvarProgresso();
  atualizarTemporizador();
}

function pausarTimer() {
  const timer = timerDoPasso(progresso.passo);
  timer.restanteMs = restanteMs(timer);
  timer.rodandoDesde = null;
  salvarProgresso();
  atualizarTemporizador();
}

function zerarTimer() {
  delete progresso.timers[progresso.passo];
  salvarProgresso();
  atualizarTemporizador();
}

/** O navegador só deixa tocar som depois de um clique: o contexto de áudio nasce no "Iniciar tempo". */
function prepararSom() {
  try {
    audio ??= new AudioContext();
    audio.resume();
  } catch {
    audio = null;
  }
}

function tocarAlarme() {
  navigator.vibrate?.([400, 200, 400, 200, 400]);
  if (!audio) return;
  for (let i = 0; i < 3; i++) {
    const oscilador = audio.createOscillator();
    oscilador.frequency.value = 880;
    oscilador.connect(audio.destination);
    oscilador.start(audio.currentTime + i * 0.6);
    oscilador.stop(audio.currentTime + i * 0.6 + 0.35);
  }
}

// ---------- Botões

function prepararBotoes() {
  $("anterior").onclick = () => irParaPasso(progresso.passo - 1);
  $("proximo").onclick = () => irParaPasso(progresso.passo + 1);
  $("timer-iniciar").onclick = iniciarTimer;
  $("timer-pausar").onclick = pausarTimer;
  $("timer-zerar").onclick = zerarTimer;
  $("iniciar-op").onclick = iniciarOp;
  $("registrar-perda").onclick = abrirPerda;
  $("finalizar").onclick = abrirConclusao;
  $("cancelar-perda").onclick = () => $("dialogo-perda").close();
  $("alarme-ok").onclick = fecharAlarme;
  $("form-perda").elements.unidades.oninput = sugerirPeso;
  $("form-perda").elements.pesoKg.oninput = () => {
    pesoSugerido = false;
  };
  $("cancelar-concluir").onclick = () => $("dialogo-concluir").close();
  $("form-perda").onsubmit = registrarPerda;
  $("form-concluir").onsubmit = concluirOp;
  document.addEventListener("keydown", atalhoDeTeclado);
}

// ---------- Atalhos de teclado (como num player de vídeo): Espaço = cronômetro, ← → = passos

/** Fora de campo de texto e de janela aberta, Espaço liga e pausa o tempo e as setas trocam de passo. */
function atalhoDeTeclado(evento) {
  if (evento.altKey || evento.ctrlKey || evento.metaKey || evento.repeat) return;
  if (document.querySelector("dialog[open]") || evento.target.closest?.("input, textarea, select, [contenteditable]")) return;
  const feito = { " ": alternarTimer, ArrowRight: () => mudarPasso(1), ArrowLeft: () => mudarPasso(-1) }[evento.key]?.();
  if (feito) evento.preventDefault();
}

/** Inicia, continua ou pausa o tempo do passo atual. Devolve false se o passo não tem tempo para mexer. */
function alternarTimer() {
  if ($("temporizador").hidden) return false;
  const timer = timerDoPasso(progresso.passo);
  const restante = restanteMs(timer);
  if (restante === 0) return false;
  if (timer.rodandoDesde) pausarTimer();
  else iniciarTimer();
  return true;
}

function mudarPasso(delta) {
  const destino = progresso.passo + delta;
  if (destino < 0 || destino >= execucao.passos.length) return false;
  irParaPasso(destino);
  return true;
}

async function iniciarOp() {
  $("iniciar-op").disabled = true;
  try {
    await apiFetch(`/api/ordens-producao/${opId}/iniciar`, { metodo: "PATCH" });
    execucao.status = "EM_ANDAMENTO";
    mostrarMensagem($("mensagem"), "OP iniciada. Bom trabalho!");
    desenharCabecalho();
    desenharPasso();
  } catch (erro) {
    mostrarMensagem($("mensagem"), erro.message, true);
  } finally {
    $("iniciar-op").disabled = false;
  }
}

// ---------- Perdas (RF011): sempre do produto da OP, em kg (Q12)

/** Massa crua por unidade do lote, em kg; null se a ficha só tem ingredientes contados em unidade. */
function calcularKgPorUnidade() {
  const kg = execucao.ingredientes.reduce(
    (soma, ingrediente) => soma + Number(ingrediente.quantidade) * (KG_POR_UNIDADE_DE_MEDIDA[ingrediente.unidadeMedida] ?? 0), 0);
  return kg > 0 && execucao.quantidadeUnidades > 0 ? kg / execucao.quantidadeUnidades : null;
}

/** Ao digitar as unidades perdidas, preenche o peso pela massa da receita (se a pessoa não digitou um peso). */
function sugerirPeso() {
  const form = $("form-perda");
  const unidades = Number(form.elements.unidades.value);
  if (!kgPorUnidade || !(unidades > 0)) return;
  if (form.elements.pesoKg.value === "" || pesoSugerido) {
    form.elements.pesoKg.value = String(Math.round(unidades * kgPorUnidade * 1000) / 1000);
    pesoSugerido = true;
  }
}

function abrirPerda() {
  const form = $("form-perda");
  form.reset();
  pesoSugerido = false;
  $("dica-peso").textContent = kgPorUnidade
    ? `Informe as unidades e o peso é preenchido pela receita (cerca de ${formatarNumero(Math.round(kgPorUnidade * 1000))} g de massa por unidade). Se pesou na balança, use o peso da balança.`
    : "";
  const etapas = [...new Set(execucao.passos.map((passo) => passo.etapa))];
  const atual = execucao.passos[progresso.passo]?.etapa;
  form.elements.etapa.replaceChildren(
    ...etapas.map((etapa) => el("option", { value: etapa, textContent: etapa, selected: etapa === atual })),
    el("option", { value: "Outra", textContent: "Outra", selected: !atual }));
  $("erro-perda").textContent = "";
  $("dialogo-perda").showModal();
  form.elements.unidades.focus();
}

async function registrarPerda(evento) {
  evento.preventDefault();
  const form = $("form-perda");
  const pesoKg = Number(form.elements.pesoKg.value);
  const unidades = form.elements.unidades.value ? Number(form.elements.unidades.value) : null;
  if (!(pesoKg > 0)) {
    $("erro-perda").textContent = "Informe o peso perdido em kg (maior que zero).";
    return;
  }
  if (unidades !== null && (!Number.isInteger(unidades) || unidades < 1)) {
    $("erro-perda").textContent = "Unidades: deixe em branco ou informe um número inteiro a partir de 1.";
    return;
  }
  $("salvar-perda").disabled = true;
  try {
    const perda = await apiFetch(`/api/ordens-producao/${opId}/perdas`, {
      metodo: "POST",
      corpo: { etapa: form.elements.etapa.value, pesoKg, unidades, motivo: form.elements.motivo.value.trim() },
    });
    $("dialogo-perda").close();
    mostrarMensagem($("mensagem"), `Perda registrada: ${formatarNumero(perda.pesoKg)} kg na etapa ${perda.etapa}.`);
    carregarPerdas();
  } catch (erro) {
    $("erro-perda").textContent = erro.message;
  } finally {
    $("salvar-perda").disabled = false;
  }
}

async function carregarPerdas() {
  try {
    const perdas = await apiFetch(`/api/ordens-producao/${opId}/perdas`);
    const total = perdas.reduce((soma, perda) => soma + Number(perda.pesoKg), 0);
    $("perdas-contagem").textContent = perdas.length
      ? `${perdas.length} perda(s), ${formatarNumero(total)} kg no total.`
      : "Nenhuma perda registrada.";
    $("perdas").replaceChildren(...perdas.map((perda) => el("tr", {},
      ...[perda.etapa, `${formatarNumero(perda.pesoKg)} kg`, perda.unidades ?? "—", perda.motivo,
        perda.registradoPorNome, formatarHorario(perda.registradoEm)]
        .map((texto) => el("td", { textContent: String(texto) })))));
  } catch (erro) {
    mostrarMensagem($("mensagem"), erro.message, true);
  }
}

// ---------- Concluir: a quantidade produzida já vem com a prevista (Q13)

function abrirConclusao() {
  const form = $("form-concluir");
  form.elements.quantidade.value = execucao.quantidadeUnidades;
  $("concluir-texto").textContent = `${execucao.fichaNome} · prevista: ${execucao.quantidadeUnidades} un.`;
  $("erro-concluir").textContent = "";
  $("dialogo-concluir").showModal();
  form.elements.quantidade.select();
}

async function concluirOp(evento) {
  evento.preventDefault();
  const quantidade = Number($("form-concluir").elements.quantidade.value);
  if (!Number.isInteger(quantidade) || quantidade < 0) {
    $("erro-concluir").textContent = "Informe um número inteiro de unidades (zero ou mais).";
    return;
  }
  $("confirmar-concluir").disabled = true;
  try {
    await apiFetch(`/api/ordens-producao/${opId}/concluir`, { metodo: "PATCH", corpo: { quantidadeProduzida: quantidade } });
    $("dialogo-concluir").close();
    apagarProgresso();
    progresso.timers = {};
    execucao.status = "CONCLUIDA";
    mostrarMensagem($("mensagem"), `OP concluída com ${quantidade} unidades.`);
    desenharCabecalho();
    desenharPasso();
  } catch (erro) {
    $("erro-concluir").textContent = erro.message;
  } finally {
    $("confirmar-concluir").disabled = false;
  }
}

// ---------- Progresso salvo no navegador (RNF-C002)

function chaveProgresso() {
  return `smartprod.execucao.${opId}`;
}

function lerProgresso() {
  try {
    return JSON.parse(localStorage.getItem(chaveProgresso()));
  } catch {
    return null;
  }
}

function salvarProgresso() {
  try {
    localStorage.setItem(chaveProgresso(), JSON.stringify(progresso));
  } catch {
    // sem localStorage (modo privado), o progresso vale só até recarregar
  }
}

function apagarProgresso() {
  try {
    localStorage.removeItem(chaveProgresso());
  } catch {
    // ignora
  }
}
