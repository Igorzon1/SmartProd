import { exigirLogin } from "./core/auth.js";
import { renderizarMenu, ITENS_MENU } from "./core/menu.js";
import { RAIZ } from "./core/config.js";
const s = exigirLogin();
if (s) {
  const gerente = s.usuario.perfil === "GERENTE";
  renderizarMenu(document.querySelector("#menu"), s.usuario.perfil);
  document.querySelector("#saudacao").textContent =
    "Olá, " + s.usuario.nome + ".";
  document.querySelector("#perfil").textContent = gerente
    ? "Área do gerente · Cadastros e gestão"
    : "Área do funcionário · Vamos começar?";
  document.querySelector("#titulo").textContent = gerente
    ? "Organize a base de cada fornada."
    : "Sua próxima etapa está aqui.";
  document.querySelector("#descricao").textContent = gerente
    ? "Mantenha sua equipe e seus insumos atualizados."
    : "Acesse o painel da cozinha e acompanhe o preparo.";
  document.querySelector("#aviso").hidden = !new URLSearchParams(
    location.search,
  ).has("acessoNegado");
  if (!gerente) document.body.classList.add("operacional");
  const nomes = gerente
    ? ["Usuários", "Insumos", "Kanban"]
    : ["Kanban", "Execução", "Calculadora"];
  for (const item of ITENS_MENU.filter((i) => nomes.includes(i.rotulo))) {
    const a = document.createElement("a");
    a.className = "card atalho";
    a.href = new URL(item.href, RAIZ);
    const h = document.createElement("h2");
    h.textContent = item.rotulo;
    const span = document.createElement("span");
    span.textContent = "Acessar →";
    a.append(h, span);
    document.querySelector("#atalhos").append(a);
  }
}
