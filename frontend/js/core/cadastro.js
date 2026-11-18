// Base dos cadastros da dupla 1. Dados da API são inseridos com textContent.
import { exigirLogin } from "./auth.js";
import { renderizarMenu } from "./menu.js";
import { apiFetch } from "./api.js";
export function iniciarCadastro(tipo) {
  const sessao = exigirLogin(["GERENTE"]);
  if (!sessao) return;
  const $ = (s) => document.querySelector(s),
    u = tipo === "usuarios",
    url = "/api/" + tipo,
    editor = $("#editor"),
    form = $("#formulario"),
    confirmacao = $("#confirmacao");
  renderizarMenu($("#menu"), sessao.usuario.perfil);
  let dados = [],
    editando = null,
    alvo = null,
    carregando = false,
    salvando = false;
  const mensagem = (s, erro = false) => {
    $("#mensagem").textContent = s;
    $("#mensagem").className = erro ? "erro" : "sucesso";
  };
  function desenhar() {
    const busca = $("#busca").value.trim().toLocaleLowerCase("pt-BR"),
      status = $("#status").value,
      lista = dados.filter(
        (d) =>
          (d.nome + " " + (d.email || ""))
            .toLocaleLowerCase("pt-BR")
            .includes(busca) &&
          (status === "todos" || (status === "ativos") === d.ativo),
      );
    $("#linhas").replaceChildren();
    $("#contagem").textContent = lista.length
      ? lista.length + " cadastro(s) encontrado(s)"
      : "Nenhum cadastro encontrado.";
    for (const d of lista) {
      const tr = document.createElement("tr"),
        valores = u
          ? [
              d.nome,
              d.email,
              d.perfil === "GERENTE" ? "Gerente" : "Funcionário",
            ]
          : [
              d.nome,
              { G: "g", KG: "kg", ML: "ml", L: "L", UN: "un" }[d.unidadeMedida],
              new Intl.NumberFormat("pt-BR", {
                style: "currency",
                currency: "BRL",
                maximumFractionDigits: 4,
              }).format(d.custoUnitario),
            ];
      for (const v of valores) {
        const td = document.createElement("td");
        td.textContent = v;
        tr.append(td);
      }
      const tdStatus = document.createElement("td"),
        badge = document.createElement("span");
      badge.className = "badge" + (d.ativo ? "" : " badge--inativo");
      badge.textContent = d.ativo ? "Ativo" : "Inativo";
      tdStatus.append(badge);
      tr.append(tdStatus);
      const td = document.createElement("td"),
        acoes = document.createElement("div");
      acoes.className = "acoes";
      const editar = document.createElement("button");
      editar.className = "btn btn--secundario";
      editar.textContent = "Editar";
      editar.setAttribute("aria-label", "Editar " + d.nome);
      editar.onclick = () => abrir(d);
      acoes.append(editar);
      if (d.ativo && !(u && d.id === sessao.usuario.id)) {
        const b = document.createElement("button");
        b.className = "btn btn--secundario";
        b.textContent = "Inativar";
        b.setAttribute("aria-label", "Inativar " + d.nome);
        b.onclick = () => {
          alvo = d;
          $("#confirmar-texto").textContent =
            d.nome + " ficará inativo. O histórico será preservado.";
          $("#erro-confirmar").textContent = "";
          confirmacao.showModal();
        };
        acoes.append(b);
      }
      if (!d.ativo) {
        const reativar = document.createElement("button");
        reativar.className = "btn btn--secundario";
        reativar.textContent = "Reativar";
        reativar.setAttribute("aria-label", "Reativar " + d.nome);
        reativar.onclick = () => reativarCadastro(d, reativar);
        acoes.append(reativar);
      }
      td.append(acoes);
      tr.append(td);
      $("#linhas").append(tr);
    }
  }
  // Desfaz uma inativação feita por engano (PATCH /reativar, só Gerente).
  async function reativarCadastro(d, botao) {
    botao.disabled = true;
    try {
      await apiFetch(url + "/" + d.id + "/reativar", { metodo: "PATCH" });
      mensagem(d.nome + " foi reativado.");
      await carregar();
    } catch (e) {
      mensagem(e.message, true);
      botao.disabled = false;
    }
  }
  async function carregar() {
    if (carregando) return;
    carregando = true;
    $("#recarregar").disabled = true;
    $("#contagem").textContent = "Carregando…";
    try {
      dados = await apiFetch(url + (u ? "" : "?incluirInativos=true"));
      desenhar();
    } catch (e) {
      mensagem(e.message, true);
      $("#contagem").textContent =
        "Não foi possível carregar. Use Atualizar para tentar novamente.";
    } finally {
      carregando = false;
      $("#recarregar").disabled = false;
    }
  }
  function abrir(d = null) {
    editando = d;
    form.reset();
    $("#erro-form").textContent = "";
    $("#editor-titulo").textContent =
      (d ? "Editar " : "Cadastrar ") + (u ? "usuário" : "insumo");
    for (const nome of u
      ? ["nome", "email", "perfil"]
      : ["nome", "unidadeMedida", "custoUnitario"])
      if (d) form.elements[nome].value = d[nome];
    if (u) form.elements.senha.required = !d;
    editor.showModal();
    form.elements.nome.focus();
  }
  $("#novo").onclick = () => abrir();
  $("#cancelar").onclick = () => editor.close();
  $("#voltar").onclick = () => confirmacao.close();
  $("#recarregar").onclick = () => {
    mensagem("");
    carregar();
  };
  $("#busca").oninput = desenhar;
  $("#status").onchange = desenhar;
  editor.addEventListener("cancel", (e) => {
    if (salvando) e.preventDefault();
  });
  confirmacao.addEventListener("cancel", (e) => {
    if ($("#inativar").disabled) e.preventDefault();
  });
  form.onsubmit = async (e) => {
    e.preventDefault();
    if (salvando) return;
    const corpo = { nome: form.elements.nome.value.trim() };
    if (!corpo.nome) {
      $("#erro-form").textContent = "Informe um nome válido.";
      return;
    }
    if (u) {
      corpo.email = form.elements.email.value.trim();
      corpo.perfil = form.elements.perfil.value;
      if (form.elements.senha.value) corpo.senha = form.elements.senha.value;
      else if (editando) corpo.senha = null;
    } else {
      corpo.unidadeMedida = form.elements.unidadeMedida.value;
      corpo.custoUnitario = Number(form.elements.custoUnitario.value);
    }
    salvando = true;
    $("#salvar").disabled = true;
    $("#cancelar").disabled = true;
    $("#erro-form").textContent = "";
    try {
      await apiFetch(url + (editando ? "/" + editando.id : ""), {
        metodo: editando ? "PUT" : "POST",
        corpo,
      });
      editor.close();
      form.reset();
      mensagem("Cadastro salvo com sucesso.");
      await carregar();
    } catch (e) {
      $("#erro-form").textContent = e.message;
    } finally {
      salvando = false;
      $("#salvar").disabled = false;
      $("#cancelar").disabled = false;
    }
  };
  $("#inativar").onclick = async () => {
    $("#inativar").disabled = true;
    $("#voltar").disabled = true;
    try {
      await apiFetch(url + "/" + alvo.id + "/inativar", { metodo: "PATCH" });
      confirmacao.close();
      mensagem("Cadastro inativado.");
      await carregar();
    } catch (e) {
      $("#erro-confirmar").textContent = e.message;
    } finally {
      $("#inativar").disabled = false;
      $("#voltar").disabled = false;
    }
  };
  carregar();
}
