// Configuração compartilhada. Dona: dupla 1.

/** URL da pasta raiz do frontend (js/core/ fica dois níveis abaixo). Serve para montar links de qualquer página. */
export const RAIZ = new URL('../../', import.meta.url);

export const config = {
  // URL base da API. Em produção, troque pelo endereço do backend hospedado.
  apiUrl: 'http://localhost:8080',

  // Enquanto o login (RF001) não está integrado, simula uma sessão para as páginas abrirem.
  // A dupla 1 troca para false quando o login real funcionar.
  usarSessaoFalsa: true,
  perfilSessaoFalsa: 'GERENTE' // 'GERENTE' ou 'FUNCIONARIO', para testar cada visão
};
