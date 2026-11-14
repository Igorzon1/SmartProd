import { RAIZ } from "./config.js";
const CHAVE = "smartprod.sessao";
// O backend valida o JWT em cada chamada. Esta checagem local apenas evita
// mostrar uma sessão cuja expiração já pode ser lida do token.
function tokenExpirado(token) {
  try {
    const parte = token.split(".")[1];
    if (!parte) return false;
    const base64 = parte.replace(/-/g, "+").replace(/_/g, "/");
    const preenchido = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
    const { exp } = JSON.parse(atob(preenchido));
    return typeof exp === "number" && exp * 1000 <= Date.now();
  } catch {
    return false;
  }
}
// Nunca armazene senhas. A sessão fica limitada à aba do navegador.
export function obterSessao() {
  try {
    const s = JSON.parse(sessionStorage.getItem(CHAVE));
    return s?.token &&
      ["GERENTE", "FUNCIONARIO"].includes(s.usuario?.perfil) &&
      !tokenExpirado(s.token)
      ? s
      : null;
  } catch {
    return null;
  }
}
export function salvarSessao(s) {
  sessionStorage.setItem(CHAVE, JSON.stringify(s));
}
export function obterToken() {
  return obterSessao()?.token ?? null;
}
export function sair() {
  sessionStorage.removeItem(CHAVE);
  localStorage.removeItem(CHAVE);
  location.replace(new URL("pages/login/index.html", RAIZ));
}
// Chame antes de buscar dados ou renderizar qualquer página protegida.
export function exigirLogin(perfis) {
  const s = obterSessao();
  if (!s) {
    location.replace(new URL("pages/login/index.html", RAIZ));
    return null;
  }
  if (perfis && !perfis.includes(s.usuario.perfil)) {
    location.replace(new URL("index.html?acessoNegado=1", RAIZ));
    return null;
  }
  return s;
}
