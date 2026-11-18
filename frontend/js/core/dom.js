// Montagem de elementos sem innerHTML (os dados da API sempre entram como texto). Dona: dupla 1.

/**
 * el("button", { className: "btn", textContent: "Salvar", "aria-label": "Salvar ficha" }, filho1, filho2)
 * Atributos aria-* e data-* viram setAttribute; o resto vira propriedade do elemento.
 */
export function el(tag, props = {}, ...filhos) {
  const elemento = document.createElement(tag);
  for (const [chave, valor] of Object.entries(props)) {
    if (valor === undefined || valor === null) continue;
    if (chave.startsWith("aria-") || chave.startsWith("data-")) elemento.setAttribute(chave, valor);
    else elemento[chave] = valor;
  }
  elemento.append(...filhos.filter((filho) => filho !== null && filho !== undefined && filho !== false));
  return elemento;
}

/** Mostra uma mensagem de sucesso ou de erro num elemento com role="status". */
export function mostrarMensagem(elemento, texto, erro = false) {
  elemento.textContent = texto;
  elemento.className = texto ? (erro ? "erro" : "sucesso") : "";
}
