// Configuração compartilhada pelas três duplas. Dona: dupla 1.
/** URL da pasta raiz do frontend (js/core/ fica dois níveis abaixo). Serve para montar links de qualquer página. */
export const RAIZ = new URL("../../", import.meta.url);

export const config = {
  // URL base da API. Em produção, troque pelo endereço do backend hospedado.
  apiUrl: "http://localhost:8080",
};
