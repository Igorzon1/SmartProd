import { config } from "./config.js";
import { obterToken, sair } from "./auth.js";
export class ErroApi extends Error {
  constructor(status, problema) {
    super(
      problema?.detail ||
        problema?.title ||
        {
          401: "E-mail ou senha inválidos.",
          403: "Você não tem permissão para esta ação.",
          409: "Cadastro duplicado ou ação não permitida.",
        }[status] ||
        "Não foi possível concluir a solicitação.",
    );
    this.status = status;
    this.problema = problema;
  }
}
// Uso: apiFetch('/api/insumos', {metodo:'POST',corpo:dados}).
export async function apiFetch(caminho, { metodo = "GET", corpo } = {}) {
  const headers = { Accept: "application/json" },
    token = obterToken(),
    login = caminho === "/api/auth/login";
  if (token && !login) headers.Authorization = "Bearer " + token;
  if (corpo !== undefined) headers["Content-Type"] = "application/json";
  let r;
  try {
    r = await fetch(config.apiUrl + caminho, {
      method: metodo,
      headers,
      body: corpo === undefined ? undefined : JSON.stringify(corpo),
      signal: AbortSignal.timeout(15000),
    });
  } catch {
    throw new ErroApi(0, {
      detail:
        "Não foi possível conectar ao servidor. Verifique a conexão e tente novamente.",
    });
  }
  let dados = null;
  const texto = await r.text();
  try {
    dados = texto ? JSON.parse(texto) : null;
  } catch {}
  if (r.status === 401 && !login) sair();
  if (!r.ok) throw new ErroApi(r.status, dados);
  // Avisa que a chamada deu certo. O tutorial (js/core/tutorial.js) usa isso para marcar as mini tarefas.
  document.dispatchEvent(new CustomEvent("smartprod:api", { detail: { metodo, caminho } }));
  return dados;
}
