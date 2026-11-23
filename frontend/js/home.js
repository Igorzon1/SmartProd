// Página inicial (dupla 1): saudação, primeiros passos do Gerente e atalhos com descrição.
import { exigirLogin } from "./core/auth.js";
import { renderizarMenu, ITENS_MENU } from "./core/menu.js";
import { RAIZ } from "./core/config.js";
import { apiFetch } from "./core/api.js";
import { el } from "./core/dom.js";
import { icone } from "./core/icones.js";

/** O que cada atalho faz, para quem chega pela primeira vez. */
const DESCRICOES = {
  Kanban: "Veja as OPs pendentes, em andamento e concluídas hoje.",
  "Execução": "Siga o passo a passo da OP, com cronômetro e registro de perdas.",
  Calculadora: "Quanto usar de cada ingrediente para a quantidade que você precisa.",
  "Ordens de produção": "Abra uma OP com produto, quantidade, entrega e responsável.",
  "Fichas técnicas": "Receitas com Porcentagem do Padeiro, custo e modo de preparo.",
  Insumos: "Ingredientes com unidade de medida e custo.",
  "Usuários": "Sua equipe e quem acessa o sistema.",
  Dashboard: "Rendimento, desperdício e andamento da produção.",
};

const ATALHOS_GERENTE = ["Dashboard", "Ordens de produção", "Kanban", "Fichas técnicas", "Insumos", "Usuários"];
const ATALHOS_FUNCIONARIO = ["Kanban", "Execução", "Calculadora"];

const $ = (seletor) => document.querySelector(seletor);
const link = (href) => new URL(href, RAIZ).href;

const s = exigirLogin();
if (s) {
  const gerente = s.usuario.perfil === "GERENTE";
  renderizarMenu($("#menu"), s.usuario.perfil);
  $("#saudacao").textContent = "Olá, " + s.usuario.nome + ".";
  $("#perfil").textContent = gerente
    ? "Área do gerente · Cadastros e gestão"
    : "Área do funcionário · Vamos começar?";
  $("#titulo").textContent = gerente
    ? "Organize a base de cada fornada."
    : "Sua próxima etapa está aqui.";
  $("#descricao").textContent = gerente
    ? "Mantenha sua equipe e seus insumos atualizados."
    : "Acesse o painel da cozinha e acompanhe o preparo.";
  $("#aviso").hidden = !new URLSearchParams(location.search).has("acessoNegado");
  if (!gerente) document.body.classList.add("operacional");

  const nomes = gerente ? ATALHOS_GERENTE : ATALHOS_FUNCIONARIO;
  for (const nome of nomes) {
    const item = ITENS_MENU.find((i) => i.rotulo === nome);
    if (!item) continue;
    $("#atalhos").append(el("a", { className: "card atalho", href: link(item.href) },
      el("h2", {}, icone(item.icone, "icone icone--atalho"), el("span", { textContent: item.rotulo })),
      el("p", { className: "atalho__descricao", textContent: DESCRICOES[nome] ?? "" }),
      el("span", { textContent: "Acessar →" })));
  }
  if (gerente) mostrarPrimeirosPassos().catch(() => {
    // Sem a API, a página continua com os atalhos: os primeiros passos são só uma ajuda.
  });
}

/**
 * Para o Gerente que está começando: a ordem para chegar à primeira fornada, com o que já foi feito.
 * Some quando todos os passos estão feitos.
 */
async function mostrarPrimeirosPassos() {
  const [insumos, fichas, usuarios, ops] = await Promise.all([
    apiFetch("/api/insumos"),
    apiFetch("/api/fichas-tecnicas"),
    apiFetch("/api/usuarios?ativo=true"),
    apiFetch("/api/ordens-producao"),
  ]);
  const funcionarios = usuarios.filter((u) => u.perfil === "FUNCIONARIO").length;
  const passos = [
    { feito: insumos.length > 0, texto: "Cadastre os insumos (ingredientes e quanto custam)",
      pronto: `${insumos.length} insumo(s) cadastrado(s)`, acao: "Cadastrar insumos", href: "pages/insumos/index.html" },
    { feito: fichas.length > 0, texto: "Monte a ficha técnica de um produto",
      pronto: `${fichas.length} ficha(s) técnica(s)`, acao: "Criar ficha técnica", href: "pages/fichas-tecnicas/index.html" },
    { feito: funcionarios > 0, texto: "Cadastre quem trabalha na cozinha",
      pronto: `${funcionarios} funcionário(s)`, acao: "Cadastrar equipe", href: "pages/usuarios/index.html" },
    { feito: ops.length > 0, texto: "Abra uma ordem de produção para hoje",
      pronto: `${ops.length} OP(s) no Kanban de hoje`, acao: "Abrir OP", href: "pages/ordens-producao/index.html" },
  ];
  if (passos.every((p) => p.feito)) return;

  const proximo = passos.findIndex((p) => !p.feito);
  $("#passos-lista").replaceChildren(...passos.map((passo, i) => el("li",
    { className: `passo-inicial${passo.feito ? " passo-inicial--feito" : ""}` },
    el("span", { className: "passo-inicial__marca", "aria-hidden": "true", textContent: passo.feito ? "✓" : String(i + 1) }),
    el("span", { className: "passo-inicial__texto" },
      el("strong", { textContent: passo.texto }),
      passo.feito ? el("small", { textContent: `Feito: ${passo.pronto}.` }) : null),
    passo.feito ? null : el("a", {
      className: i === proximo ? "btn" : "btn btn--secundario", href: link(passo.href), textContent: passo.acao }))));
  $("#primeiros-passos").hidden = false;
  $(".hero").hidden = true;
}
