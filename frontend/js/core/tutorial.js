// Tutorial de primeiro acesso. Dona: dupla 1.
// 1) Boas-vindas: uma janela curta, que abre sozinha na página inicial no primeiro acesso.
// 2) Trilha: um painel no canto que leva a pessoa de tela em tela. Em cada parada, uma frase do que é a
//    tela, uma do que ela ganha e uma mini tarefa de verdade (cadastrar um insumo, abrir uma OP...), que se
//    marca sozinha quando a ação dá certo. Dá para pular a tarefa, minimizar o painel ou sair da trilha.
// "Como usar", no menu, recomeça tudo. O progresso fica no localStorage, por usuário: em outro aparelho o
// tutorial começa de novo. Os textos do Funcionário não falam de custo nem de valores em reais.
// As tarefas são percebidas pelo aviso "smartprod:api" do api.js (chamada que deu certo) ou por um clique
// ou envio de formulário na tela; assim nenhuma tela das outras duplas precisa conhecer o tutorial.
import { RAIZ } from "./config.js";
import { obterSessao } from "./auth.js";
import { el } from "./dom.js";
import { icone } from "./icones.js";

/** Texto da janela de boas-vindas, por perfil. */
const BOAS_VINDAS = {
  GERENTE: {
    resumo: "Sua padaria funcionando como uma esteira: cada receita sai igual, no horário, e você vê tudo o que acontece.",
    como: "O SmartProd acompanha a produção do começo ao fim. Você cadastra ingredientes e receitas, abre as ordens "
      + "de produção, a equipe segue o passo a passo na cozinha e os números chegam ao seu painel.",
    ganhos: [
      "Qualidade igual em toda fornada, não importa quem prepara",
      "Menos ingrediente e menos fornada jogados fora",
      "Produto pronto no horário, sem faltar no balcão",
      "Custo, rendimento e desperdício à vista, na hora",
    ],
  },
  FUNCIONARIO: {
    resumo: "Seu guia na cozinha.",
    como: "Aqui você vê o que precisa produzir, segue a receita passo a passo e avisa quando algo dá errado. "
      + "Tudo pela tela, no tablet ou no computador.",
    ganhos: [
      "Menos dúvida: a receita e as quantidades estão sempre na tela",
      "Menos retrabalho e menos fornada perdida",
      "Você sabe exatamente o que fazer e em que ordem",
    ],
  },
};

/**
 * Paradas da trilha, por perfil, na ordem em que a padaria usa o sistema. `tela` é a pasta em pages/.
 * `feitaQuando` lista o que conta como tarefa feita: uma chamada à API que deu certo (método e caminho) ou
 * um clique/envio num elemento da tela. Chamada GET só conta na própria tela da parada; as que gravam algo
 * contam em qualquer tela (ex.: iniciar a OP pelo passo a passo em vez do Kanban).
 */
const TRILHAS = {
  GERENTE: [
    {
      tela: "insumos", href: "pages/insumos/index.html", icone: "insumos", titulo: "Insumos",
      oque: "Os ingredientes da padaria, com a unidade e o custo.",
      ganho: "o custo de cada receita e de cada perda sai sozinho, em reais.",
      tarefa: "Cadastre um insumo em \"+ Novo insumo\". Não sabe o custo por grama? Use \"Calcular pelo preço da embalagem\".",
      feitaQuando: [{ api: "POST", caminho: /^\/api\/insumos$/ }],
    },
    {
      tela: "fichas-tecnicas", href: "pages/fichas-tecnicas/index.html", icone: "fichas", titulo: "Fichas técnicas",
      oque: "A receita de cada produto: ingredientes, rendimento e modo de preparo.",
      ganho: "o pão sai igual com qualquer padeiro, e fica fácil ensinar quem acabou de chegar.",
      tarefa: "Abra uma ficha em \"Editar\" e veja a Porcentagem do Padeiro (ou crie a sua em \"+ Nova ficha\").",
      feitaQuando: [
        { api: "GET", caminho: /^\/api\/fichas-tecnicas\/\d+$/ },
        { api: "POST", caminho: /^\/api\/fichas-tecnicas$/ },
      ],
    },
    {
      tela: "calculadora", href: "pages/calculadora/index.html", icone: "calculadora", titulo: "Calculadora",
      oque: "Ajusta a receita para a quantidade que você quer ou para o estoque que você tem.",
      ganho: "fim da conta de cabeça e do erro de dosagem.",
      tarefa: "Escolha uma receita, digite outra quantidade e toque em \"Calcular\".",
      feitaQuando: [
        { api: "GET", caminho: /^\/api\/fichas-tecnicas\/\d+\/calculo(\?|$)/ },
        { api: "POST", caminho: /^\/api\/fichas-tecnicas\/\d+\/calculo-por-estoque$/ },
      ],
    },
    {
      tela: "usuarios", href: "pages/usuarios/index.html", icone: "usuarios", titulo: "Usuários",
      oque: "Sua equipe, cada pessoa com o próprio acesso.",
      ganho: "cada um vê só o que precisa, e funcionários não veem custos.",
      tarefa: "Cadastre alguém da equipe em \"+ Novo usuário\".",
      feitaQuando: [{ api: "POST", caminho: /^\/api\/usuarios$/ }],
    },
    {
      tela: "ordens-producao", href: "pages/ordens-producao/index.html", icone: "ordens", titulo: "Ordens de produção",
      oque: "O pedido para a cozinha: o que, quanto, até que horas e quem faz.",
      ganho: "a equipe sabe o que produzir sem precisar perguntar.",
      tarefa: "Preencha o formulário e toque em \"Abrir OP\".",
      feitaQuando: [{ api: "POST", caminho: /^\/api\/ordens-producao$/ }],
    },
    {
      tela: "kanban", href: "pages/kanban/index.html", icone: "kanban", titulo: "Kanban",
      oque: "O quadro da cozinha: pendentes, em andamento e concluídas hoje.",
      ganho: "você vê os atrasos antes que virem falta no balcão.",
      tarefa: "Toque em \"Iniciar\" numa OP pendente.",
      feitaQuando: [{ api: "PATCH", caminho: /^\/api\/ordens-producao\/\d+\/iniciar$/ }],
    },
    {
      tela: "execucao", href: "pages/execucao/index.html", icone: "execucao", titulo: "Passo a passo",
      oque: "A receita guiada no tablet, com cronômetro e registro de perdas.",
      ganho: "menos erro na cozinha: ninguém precisa decorar a receita.",
      tarefa: "Toque em \"Abrir passo a passo\" numa OP em andamento.",
      feitaQuando: [{ api: "GET", caminho: /^\/api\/ordens-producao\/\d+\/execucao$/ }],
    },
    {
      tela: "dashboard", href: "pages/dashboard/index.html", icone: "dashboard", titulo: "Dashboard",
      oque: "A produção em números: rendimento, desperdício e situação das OPs.",
      ganho: "você decide com dados e vê quanto dinheiro está indo para o lixo.",
      tarefa: "Escolha outro período nos filtros e toque em \"Atualizar\".",
      feitaQuando: [{ dom: "submit", seletor: "#filtros" }],
    },
  ],
  FUNCIONARIO: [
    {
      tela: "kanban", href: "pages/kanban/index.html", icone: "kanban", titulo: "Kanban",
      oque: "Sua fila de produção: o que fazer, quanto e até que horas.",
      ganho: "você sabe o que é mais urgente sem precisar perguntar.",
      tarefa: "Toque em \"Iniciar\" numa OP pendente sua.",
      feitaQuando: [{ api: "PATCH", caminho: /^\/api\/ordens-producao\/\d+\/iniciar$/ }],
    },
    {
      tela: "execucao", href: "pages/execucao/index.html", icone: "execucao", titulo: "Passo a passo",
      oque: "A receita uma etapa de cada vez, já com as quantidades do lote.",
      ganho: "não precisa decorar nem fazer conta, e o cronômetro avisa a hora.",
      tarefa: "Toque em \"Abrir passo a passo\" numa OP em andamento.",
      feitaQuando: [{ api: "GET", caminho: /^\/api\/ordens-producao\/\d+\/execucao$/ }],
    },
    {
      tela: "execucao", href: "pages/execucao/index.html", icone: "alerta", titulo: "Registrar perda",
      oque: "Quando algo dá errado, você avisa em que etapa, quanto se perdeu e por quê.",
      ganho: "não é para achar culpado: é para a receita e o processo melhorarem.",
      tarefa: "Numa OP em andamento, toque em \"Registrar perda\" para conhecer o formulário. Pode cancelar: nada é salvo.",
      feitaQuando: [{ dom: "click", seletor: "#registrar-perda" }],
    },
    {
      tela: "calculadora", href: "pages/calculadora/index.html", icone: "calculadora", titulo: "Calculadora",
      oque: "Tira dúvida de quantidade para outro tamanho de lote.",
      ganho: "resposta na hora, sem esperar o gerente.",
      tarefa: "Escolha uma receita, digite outra quantidade e toque em \"Calcular\".",
      feitaQuando: [
        { api: "GET", caminho: /^\/api\/fichas-tecnicas\/\d+\/calculo(\?|$)/ },
        { api: "POST", caminho: /^\/api\/fichas-tecnicas\/\d+\/calculo-por-estoque$/ },
      ],
    },
  ],
};

/** Se o localStorage falhar (modo privado bloqueado), o tutorial lembra só até recarregar a página. */
let estadoEmMemoria = { boasVindas: false, trilha: null };
let cssPronto = null;
let perfilAtual = null;
let telaAtual = null;
let painel = null;
let ouvindo = false;
/** Pergunta "Sair da trilha?" aberta no painel (não fica salva). */
let confirmandoSaida = false;
/** A tarefa acabou de ser feita nesta página: o painel comemora uma vez. */
let comemorar = false;

/**
 * Chamado pelo menu em toda página: abre as boas-vindas (só na página inicial, no primeiro acesso) ou
 * mostra o painel da trilha, se ela estiver em andamento. `href` é o do item do menu da página aberta.
 */
export async function iniciarTutorial(perfil, href) {
  perfilAtual = perfil;
  telaAtual = telaDo(href);
  ouvirTarefas();
  const estado = lerEstado();
  if (telaAtual === "inicio" && !estado.boasVindas) {
    await carregarCss();
    abrirBoasVindas();
  } else if (estado.trilha) {
    await carregarCss();
    desenharPainel();
  }
}

/** "Como usar", no menu: esquece o progresso e abre as boas-vindas de novo. */
export async function reverTutorial(perfil, href) {
  perfilAtual = perfil;
  telaAtual = telaDo(href);
  ouvirTarefas();
  salvarEstado({ boasVindas: false, trilha: null });
  removerPainel();
  await carregarCss();
  abrirBoasVindas();
}

/** "pages/fichas-tecnicas/index.html" → "fichas-tecnicas"; "index.html" → "inicio". */
function telaDo(href) {
  if (!href) return null;
  return href === "index.html" ? "inicio" : href.split("/")[1];
}

// ---------- Boas-vindas

function abrirBoasVindas() {
  const texto = BOAS_VINDAS[perfilAtual];
  const paradas = TRILHAS[perfilAtual]?.length ?? 0;
  if (!texto || document.querySelector("dialog.tutorial")) return;

  let comecarTrilha = false;
  const sozinho = el("button", { type: "button", className: "btn btn--secundario", textContent: "Explorar sozinho" });
  const comecar = el("button", { type: "button", className: "btn", textContent: "Começar a trilha" });
  const dialogo = el(
    "dialog",
    { className: "tutorial", "aria-labelledby": "tutorial-titulo" },
    el(
      "div",
      { className: "tutorial__conteudo" },
      el("div", { className: "tutorial__cabecalho" },
        el("div", { className: "tutorial__icone" }, icone("inicio")),
        el("h2", { id: "tutorial-titulo", textContent: "Bem-vindo ao SmartProd" })),
      el("p", { className: "tutorial__resumo", textContent: texto.resumo }),
      el("h3", { className: "tutorial__secao", textContent: "Como funciona" }),
      el("p", { className: "tutorial__como", textContent: texto.como }),
      el("h3", { className: "tutorial__secao", textContent: "O que você ganha" }),
      listaDeGanhos(texto.ganhos),
      el("p", { className: "tutorial__dica" }, icone("bandeira"), el("span", {
        textContent: `A trilha passa por ${paradas} telas. Em cada uma, uma explicação curta e uma mini tarefa `
          + "para você aprender fazendo. Leva poucos minutos.",
      })),
    ),
    el("div", { className: "tutorial__rodape" }, el("div", { className: "tutorial__botoes" }, sozinho, comecar)),
  );

  sozinho.onclick = () => dialogo.close();
  comecar.onclick = () => {
    comecarTrilha = true;
    dialogo.close();
  };
  // Fechar por qualquer caminho (Explorar sozinho, Esc, Começar) conta como visto.
  dialogo.addEventListener("close", () => {
    const estado = lerEstado();
    const trilha = comecarTrilha ? { etapa: 0, feita: false, minimizada: false } : estado.trilha;
    salvarEstado({ ...estado, boasVindas: true, trilha });
    dialogo.remove();
    if (comecarTrilha) irPara(0);
  });

  document.body.append(dialogo);
  dialogo.showModal();
  // O foco vai para "Começar a trilha" sem rolar a janela: o título continua à vista.
  comecar.focus({ preventScroll: true });
  dialogo.scrollTop = 0;
}

// ---------- Trilha

/** Leva para a tela da parada; se ela já está aberta, só redesenha o painel. */
function irPara(etapa) {
  const parada = TRILHAS[perfilAtual]?.[etapa];
  if (parada && parada.tela !== telaAtual) location.assign(new URL(parada.href, RAIZ));
  else desenharPainel();
}

/** Próxima parada (depois da tarefa feita ou pulada). Depois da última, a trilha mostra a conclusão. */
function avancar() {
  const estado = lerEstado();
  if (!estado.trilha) return;
  const etapa = estado.trilha.etapa + 1;
  salvarEstado({ ...estado, trilha: { ...estado.trilha, etapa, feita: false } });
  irPara(etapa);
}

function mudarTrilha(mudanca) {
  const estado = lerEstado();
  salvarEstado({ ...estado, trilha: mudanca === null ? null : { ...estado.trilha, ...mudanca } });
  desenharPainel();
}

function desenharPainel() {
  const trilha = lerEstado().trilha;
  const paradas = TRILHAS[perfilAtual];
  if (!trilha || !paradas) return removerPainel();
  if (!painel) criarPainel();

  if (trilha.minimizada) {
    painel.className = "trilha trilha--minimizada";
    const abrir = el("button", { type: "button", className: "btn trilha__reabrir",
      "aria-label": "Abrir a trilha de aprendizado" },
    icone("bandeira"), el("span", { textContent: `Trilha ${Math.min(trilha.etapa + 1, paradas.length)} de ${paradas.length}` }));
    abrir.onclick = () => mudarTrilha({ minimizada: false });
    return painel.replaceChildren(abrir);
  }

  painel.className = "trilha";
  if (confirmandoSaida) return painel.replaceChildren(...conteudoSaida());
  if (trilha.etapa >= paradas.length) return painel.replaceChildren(...conteudoConclusao(paradas.length));

  const parada = paradas[trilha.etapa];
  const proxima = paradas[trilha.etapa + 1];
  const aqui = parada.tela === telaAtual;
  const partes = [topo(trilha.etapa + 1, paradas.length, trilha.etapa + (trilha.feita ? 1 : 0))];

  if (trilha.feita || aqui) {
    partes.push(
      el("h2", { className: "trilha__titulo" }, icone(parada.icone), el("span", { textContent: parada.titulo })),
      el("p", { className: "trilha__oque", textContent: parada.oque }),
      el("p", { className: "trilha__ganho" }, el("strong", { textContent: "Você ganha: " }), parada.ganho),
      blocoTarefa(parada, trilha.feita),
    );
    if (trilha.feita) {
      const seguir = el("button", { type: "button", className: "btn trilha__principal",
        textContent: proxima ? `Próxima: ${proxima.titulo} →` : "Concluir a trilha" });
      seguir.onclick = avancar;
      partes.push(el("div", { className: "trilha__acoes" }, seguir));
    } else {
      const pular = el("button", { type: "button", className: "btn btn--secundario", textContent: "Pular tarefa" });
      pular.onclick = avancar;
      partes.push(el("div", { className: "trilha__acoes" }, pular));
    }
  } else {
    // Em outra tela: só diz para onde ir, sem repetir a explicação.
    const ir = el("button", { type: "button", className: "btn trilha__principal", textContent: `Ir para ${parada.titulo} →` });
    ir.onclick = () => irPara(trilha.etapa);
    partes.push(
      el("p", { className: "trilha__rotulo", textContent: "Próxima parada" }),
      el("h2", { className: "trilha__titulo" }, icone(parada.icone), el("span", { textContent: parada.titulo })),
      el("p", { className: "trilha__oque", textContent: parada.oque }),
      el("div", { className: "trilha__acoes" }, ir),
    );
  }
  painel.replaceChildren(...partes);
  comemorar = false;
}

function criarPainel() {
  painel = el("aside", { className: "trilha", "aria-label": "Trilha de aprendizado" });
  document.body.append(painel);
  document.body.classList.add("com-trilha");
  // O fim da página ganha espaço do tamanho do painel, para nada ficar escondido atrás dele.
  new ResizeObserver(() => {
    document.body.style.setProperty("--altura-trilha", `${painel?.offsetHeight ?? 0}px`);
  }).observe(painel);
}

function removerPainel() {
  painel?.remove();
  painel = null;
  confirmandoSaida = false;
  document.body.classList.remove("com-trilha");
}

/** `concluidas` enche a barra: as paradas já passadas, mais a atual quando a tarefa dela foi feita. */
function topo(numero, total, concluidas) {
  const minimizar = el("button", { type: "button", className: "trilha__icone-btn", "aria-label": "Minimizar a trilha",
    title: "Minimizar", textContent: "–" });
  minimizar.onclick = () => mudarTrilha({ minimizada: true });
  const sair = el("button", { type: "button", className: "trilha__icone-btn", "aria-label": "Sair da trilha",
    title: "Sair da trilha" }, icone("fechar"));
  sair.onclick = () => {
    confirmandoSaida = true;
    desenharPainel();
  };
  const barra = el("span");
  barra.style.width = `${Math.round((concluidas / total) * 100)}%`;
  return el(
    "div",
    { className: "trilha__topo" },
    el("p", { className: "trilha__progresso", textContent: `Trilha · ${numero} de ${total}` }),
    minimizar,
    sair,
    el("div", { className: "trilha__barra", "aria-hidden": "true" }, barra),
  );
}

function blocoTarefa(parada, feita) {
  const classes = ["trilha__tarefa", feita && "trilha__tarefa--feita", feita && comemorar && "trilha__tarefa--comemora"];
  const bloco = el(
    "div",
    { className: classes.filter(Boolean).join(" "), "aria-live": "polite" },
    el("span", { className: "trilha__marca", "aria-hidden": "true" }, feita ? icone("check") : null),
    el(
      "div",
      {},
      el("p", { className: "trilha__tarefa-rotulo", textContent: feita ? "Tarefa feita. Muito bem!" : "Sua mini tarefa" }),
      el("p", { className: "trilha__tarefa-texto", textContent: parada.tarefa }),
    ),
  );
  bloco.setAttribute("role", "status");
  return bloco;
}

function conteudoSaida() {
  const continuar = el("button", { type: "button", className: "btn", textContent: "Continuar a trilha" });
  continuar.onclick = () => {
    confirmandoSaida = false;
    desenharPainel();
  };
  const sair = el("button", { type: "button", className: "btn btn--secundario", textContent: "Sair" });
  sair.onclick = () => {
    confirmandoSaida = false;
    mudarTrilha(null);
  };
  return [
    el("h2", { className: "trilha__titulo", textContent: "Sair da trilha?" }),
    el("p", { className: "trilha__oque", textContent: "Você pode recomeçar quando quiser em \"Como usar\", no menu." }),
    el("div", { className: "trilha__acoes" }, continuar, sair),
  ];
}

function conteudoConclusao(total) {
  const fechar = el("button", { type: "button", className: "btn trilha__principal", textContent: "Fechar" });
  fechar.onclick = () => mudarTrilha(null);
  return [
    el("h2", { className: "trilha__titulo trilha__titulo--fim" }, icone("bandeira"), el("span", { textContent: "Trilha concluída!" })),
    el("p", { className: "trilha__oque", textContent: `Você passou pelas ${total} telas do SmartProd e já usou cada uma. Agora é com você!` }),
    el("p", { className: "trilha__ganho", textContent: "Para rever, use \"Como usar\", no menu." }),
    el("div", { className: "trilha__acoes" }, fechar),
  ];
}

// ---------- Mini tarefas

function ouvirTarefas() {
  if (ouvindo) return;
  ouvindo = true;
  document.addEventListener("smartprod:api", (evento) => conferirTarefa(evento.detail));
  for (const tipo of ["click", "submit"]) {
    document.addEventListener(tipo, (evento) => conferirTarefa({ dom: tipo, alvo: evento.target }), true);
  }
}

/** Marca a tarefa da parada atual quando o que aconteceu bate com ela. */
function conferirTarefa(ocorrido) {
  const estado = lerEstado();
  const trilha = estado.trilha;
  const parada = TRILHAS[perfilAtual]?.[trilha?.etapa];
  if (!trilha || !parada || trilha.feita) return;
  const bate = parada.feitaQuando.some((regra) => {
    if (regra.api) {
      return ocorrido.metodo === regra.api && regra.caminho.test(ocorrido.caminho ?? "")
        && (regra.api !== "GET" || parada.tela === telaAtual);
    }
    return ocorrido.dom === regra.dom && parada.tela === telaAtual
      && ocorrido.alvo instanceof Element && Boolean(ocorrido.alvo.closest(regra.seletor));
  });
  if (!bate) return;
  salvarEstado({ ...estado, trilha: { ...trilha, feita: true, minimizada: false } });
  comemorar = true;
  carregarCss().then(desenharPainel);
}

// ---------- Apoio

function listaDeGanhos(ganhos) {
  return el("ul", { className: "ganhos" },
    ...ganhos.map((ganho) => el("li", {}, icone("check"), el("span", { textContent: ganho }))));
}

/** Os estilos só carregam quando há algo para mostrar; o painel espera por eles para não piscar sem estilo. */
function carregarCss() {
  cssPronto ??= new Promise((pronto) => {
    const link = el("link", { rel: "stylesheet", href: new URL("css/tutorial.css", RAIZ).href });
    link.onload = pronto;
    link.onerror = pronto;
    document.head.append(link);
  });
  return cssPronto;
}

function chave() {
  const id = obterSessao()?.usuario?.id;
  return id === undefined ? null : `smartprod.tutorial.${id}`;
}

function lerEstado() {
  try {
    const salvo = JSON.parse(localStorage.getItem(chave()));
    if (!salvo) return { boasVindas: false, trilha: null };
    const trilha = salvo.trilha && Number.isInteger(salvo.trilha.etapa) ? salvo.trilha : null;
    return { boasVindas: salvo.boasVindas === true, trilha };
  } catch {
    return estadoEmMemoria;
  }
}

function salvarEstado(estado) {
  estadoEmMemoria = estado;
  try {
    if (chave()) localStorage.setItem(chave(), JSON.stringify(estado));
  } catch {
    // sem localStorage, vale o estado em memória até recarregar a página
  }
}
