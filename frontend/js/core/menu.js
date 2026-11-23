import { sair } from "./auth.js";
// Menu principal, igual em todas as páginas. Dona: dupla 1.
// Todas as páginas já estão listadas aqui; cada dupla só implementa a sua, sem editar este arquivo.
// Menu lateral no computador, faixa de ícones no tablet e botão "Menu" no celular (estilos em css/base.css).
import { RAIZ } from "./config.js";
import { icone } from "./icones.js";
import { iniciarTutorial, reverTutorial } from "./tutorial.js";

/** Itens na ordem do menu. `grupo` abre um título de seção antes do item; Dashboard fica logo no começo. */
export const ITENS_MENU = [
  { rotulo: "Início", href: "index.html", icone: "inicio", perfis: ["GERENTE", "FUNCIONARIO"] },
  { rotulo: "Dashboard", href: "pages/dashboard/index.html", icone: "dashboard", perfis: ["GERENTE"] },
  {
    rotulo: "Kanban",
    href: "pages/kanban/index.html",
    icone: "kanban",
    grupo: "Produção",
    perfis: ["GERENTE", "FUNCIONARIO"],
  },
  {
    rotulo: "Ordens de produção",
    href: "pages/ordens-producao/index.html",
    icone: "ordens",
    perfis: ["GERENTE"],
  },
  { rotulo: "Execução", href: "pages/execucao/index.html", icone: "execucao", perfis: ["GERENTE", "FUNCIONARIO"] },
  { rotulo: "Calculadora", href: "pages/calculadora/index.html", icone: "calculadora", perfis: ["GERENTE", "FUNCIONARIO"] },
  {
    rotulo: "Fichas técnicas",
    href: "pages/fichas-tecnicas/index.html",
    icone: "fichas",
    grupo: "Cadastros",
    perfis: ["GERENTE"],
  },
  { rotulo: "Insumos", href: "pages/insumos/index.html", icone: "insumos", perfis: ["GERENTE"] },
  { rotulo: "Usuários", href: "pages/usuarios/index.html", icone: "usuarios", perfis: ["GERENTE"] },
];

/** O item do menu da página aberta, ou undefined. */
function itemAtual() {
  // Depois do login o endereço é "/", que é a mesma página que "/index.html".
  const caminho = location.pathname.endsWith("/") ? `${location.pathname}index.html` : location.pathname;
  return ITENS_MENU.find((item) => new URL(item.href, RAIZ).pathname === caminho);
}

/** Preenche o elemento <nav> com os links que o perfil pode ver. */
export function renderizarMenu(elemento, perfil) {
  const atual = itemAtual();
  const itens = [];
  for (const item of ITENS_MENU.filter((i) => i.perfis.includes(perfil))) {
    if (item.grupo) {
      const grupo = document.createElement("p");
      grupo.className = "menu__grupo";
      grupo.textContent = item.grupo;
      itens.push(grupo);
    }
    const link = document.createElement("a");
    link.className = "menu__link";
    link.href = new URL(item.href, RAIZ).href;
    const rotulo = document.createElement("span");
    rotulo.textContent = item.rotulo;
    link.append(icone(item.icone), rotulo);
    if (item === atual) link.setAttribute("aria-current", "page");
    itens.push(link);
  }

  const logout = document.createElement("button");
  logout.type = "button";
  logout.className = "btn btn--secundario menu__sair";
  const textoSair = document.createElement("span");
  textoSair.textContent = "Sair";
  logout.append(icone("sair"), textoSair);
  logout.onclick = sair;

  // "Como usar" reabre o tutorial (boas-vindas e dicas de cada tela). Mesmo visual do Sair, logo acima dele.
  const ajuda = document.createElement("button");
  ajuda.type = "button";
  ajuda.className = "btn btn--secundario menu__sair menu__ajuda";
  const textoAjuda = document.createElement("span");
  textoAjuda.textContent = "Como usar";
  ajuda.append(icone("ajuda"), textoAjuda);
  ajuda.onclick = () => {
    mostrarAberto(false);
    reverTutorial(perfil, atual?.href);
  };

  const lista = document.createElement("div");
  lista.className = "menu__itens";
  lista.id = "menu-itens";
  lista.append(...itens, ajuda, logout);

  const marca = document.createElement("strong");
  marca.className = "menu__marca"; // prefixo do menu: ".marca" sozinho colide com o dashboard
  marca.textContent = "◒ SmartProd";

  // No celular os itens ficam escondidos atrás deste botão (some no computador e no tablet).
  const alternar = document.createElement("button");
  alternar.type = "button";
  alternar.className = "btn btn--secundario menu__alternar";
  alternar.setAttribute("aria-controls", lista.id);
  alternar.setAttribute("aria-expanded", "false");
  const mostrarAberto = (aberto) => {
    elemento.classList.toggle("menu--aberto", aberto);
    alternar.setAttribute("aria-expanded", String(aberto));
    const texto = document.createElement("span");
    texto.textContent = aberto ? "Fechar" : "Menu";
    alternar.replaceChildren(icone(aberto ? "fechar" : "menu"), texto);
  };
  alternar.onclick = () => mostrarAberto(!elemento.classList.contains("menu--aberto"));
  elemento.addEventListener("keydown", (evento) => {
    if (evento.key === "Escape" && elemento.classList.contains("menu--aberto")) {
      mostrarAberto(false);
      alternar.focus();
    }
  });
  mostrarAberto(false);

  const topo = document.createElement("div");
  topo.className = "menu__topo";
  topo.append(marca, alternar);
  elemento.replaceChildren(topo, lista);
  document.body.classList.add("com-menu");

  // O título da tela ganha o mesmo ícone do menu (o Início fica só com a saudação).
  const titulo = document.querySelector("main h1");
  if (atual && atual.href !== "index.html" && titulo && !titulo.querySelector(".icone")) {
    titulo.prepend(icone(atual.icone, "icone icone--titulo"));
  }

  // Tutorial de primeiro acesso: boas-vindas na página inicial e a dica da tela na primeira visita.
  // Uma falha aqui nunca pode atrapalhar a página.
  iniciarTutorial(perfil, atual?.href).catch(() => {});
}
