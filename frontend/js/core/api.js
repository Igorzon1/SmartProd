// Cliente da API. Dona: dupla 1. As outras duplas só usam apiFetch().
import { config } from './config.js';
import { obterToken, sair } from './auth.js';

/** Erro devolvido pela API. `problema` é o corpo application/problem+json, quando houver. */
export class ErroApi extends Error {
  constructor(status, problema) {
    super(problema?.detail || problema?.title || `Erro ${status}`);
    this.status = status;
    this.problema = problema;
  }
}

/**
 * Chama a API com o token do usuário logado.
 * @param {string} caminho ex.: '/api/insumos'
 * @param {{ metodo?: string, corpo?: unknown }} [opcoes] `corpo` vira JSON automaticamente.
 * @returns {Promise<any>} corpo da resposta em JSON (null se vier vazio).
 * @throws {ErroApi} para status fora de 2xx (401 com token leva ao login).
 */
export async function apiFetch(caminho, { metodo = 'GET', corpo } = {}) {
  const cabecalhos = { Accept: 'application/json' };
  const token = obterToken();
  if (token) cabecalhos.Authorization = `Bearer ${token}`;
  if (corpo !== undefined) cabecalhos['Content-Type'] = 'application/json';

  const resposta = await fetch(`${config.apiUrl}${caminho}`, {
    method: metodo,
    headers: cabecalhos,
    body: corpo !== undefined ? JSON.stringify(corpo) : undefined
  });

  const texto = await resposta.text();
  let dados = null;
  if (texto) {
    try {
      dados = JSON.parse(texto);
    } catch {
      dados = null;
    }
  }

  if (resposta.status === 401 && token) sair(); // token expirado ou inválido
  if (!resposta.ok) throw new ErroApi(resposta.status, dados);
  return dados;
}
