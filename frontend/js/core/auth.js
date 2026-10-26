// Sessão e controle de acesso por perfil. Dona: dupla 1.
import { config, RAIZ } from './config.js';

const CHAVE_SESSAO = 'smartprod.sessao';

/** Sessão esperada: { token, usuario: { id, nome, perfil } } (resposta de POST /api/auth/login). */
export function obterSessao() {
  if (config.usarSessaoFalsa) {
    return { token: null, usuario: { id: 0, nome: 'Sessão de desenvolvimento', perfil: config.perfilSessaoFalsa } };
  }
  try {
    return JSON.parse(localStorage.getItem(CHAVE_SESSAO));
  } catch {
    return null;
  }
}

export function salvarSessao(sessao) {
  try {
    localStorage.setItem(CHAVE_SESSAO, JSON.stringify(sessao));
  } catch {
    // Sem localStorage (modo privado, por exemplo): a sessão vale só até recarregar a página.
  }
}

export function obterToken() {
  return obterSessao()?.token ?? null;
}

export function sair() {
  try {
    localStorage.removeItem(CHAVE_SESSAO);
  } catch {
    // ignora
  }
  location.href = new URL('pages/login/index.html', RAIZ).href;
}

/**
 * Chame no começo de toda página protegida. Devolve a sessão, ou null depois de redirecionar.
 * @param {string[]} [perfisPermitidos] ex.: ['GERENTE']; omitido = qualquer usuário logado.
 */
export function exigirLogin(perfisPermitidos) {
  const sessao = obterSessao();
  if (!sessao?.usuario) {
    location.href = new URL('pages/login/index.html', RAIZ).href;
    return null;
  }
  if (perfisPermitidos && !perfisPermitidos.includes(sessao.usuario.perfil)) {
    location.href = new URL('index.html?acessoNegado=1', RAIZ).href;
    return null;
  }
  return sessao;
}
