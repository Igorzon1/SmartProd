import { apiFetch } from "../../js/core/api.js";
import { obterSessao, salvarSessao } from "../../js/core/auth.js";
import { RAIZ } from "../../js/core/config.js";
if (obterSessao()) location.replace(RAIZ);
const form = document.querySelector("form"),
  msg = document.querySelector("#mensagem"),
  btn = document.querySelector("#entrar");
document.querySelector("#mostrar").onclick = (e) => {
  const s = form.elements.senha,
    mostrar = s.type === "password";
  s.type = mostrar ? "text" : "password";
  e.target.textContent = mostrar ? "Ocultar senha" : "Mostrar senha";
  e.target.setAttribute("aria-pressed", mostrar);
};
form.addEventListener("submit", async (e) => {
  e.preventDefault();
  btn.disabled = true;
  btn.textContent = "Entrando…";
  msg.textContent = "";
  try {
    const s = await apiFetch("/api/auth/login", {
      metodo: "POST",
      corpo: {
        email: form.elements.email.value.trim(),
        senha: form.elements.senha.value,
      },
    });
    salvarSessao({ token: s.token, usuario: s.usuario });
    location.replace(RAIZ);
  } catch (erro) {
    msg.textContent = erro.message;
    form.elements.senha.value = "";
    form.elements.senha.focus();
  } finally {
    btn.disabled = false;
    btn.textContent = "Entrar →";
  }
});
