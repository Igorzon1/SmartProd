// Formatação comum: datas no fuso de São Paulo e números em pt-BR. Dona: dupla 1.
// A API trabalha em UTC (ISO-8601); a interface sempre mostra o horário de São Paulo.

export const FUSO = "America/Sao_Paulo";

const formatoHora = new Intl.DateTimeFormat("pt-BR", { timeZone: FUSO, hour: "2-digit", minute: "2-digit" });
const formatoDiaMes = new Intl.DateTimeFormat("pt-BR", { timeZone: FUSO, day: "2-digit", month: "2-digit" });
const formatoIso = new Intl.DateTimeFormat("en-CA", { timeZone: FUSO }); // AAAA-MM-DD
const formatoNumero = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 3 });

/** Unidades de medida como o usuário lê (a API manda G, KG, ML, L e UN). */
export const UNIDADES = { G: "g", KG: "kg", ML: "ml", L: "L", UN: "un" };

/** Hoje em São Paulo, no formato AAAA-MM-DD (o mesmo dos filtros da API). */
export function hojeSaoPaulo() {
  return formatoIso.format(new Date());
}

/** Dia (AAAA-MM-DD) de um instante da API, em São Paulo. */
export function diaSaoPaulo(instante) {
  return formatoIso.format(new Date(instante));
}

/** "14:30" se for hoje; "19/11 14:30" se for outro dia. */
export function formatarHorario(instante) {
  const data = new Date(instante);
  const hora = formatoHora.format(data);
  return diaSaoPaulo(data) === hojeSaoPaulo() ? hora : `${formatoDiaMes.format(data)} ${hora}`;
}

/** Hora atual em São Paulo ("14:30"). */
export function horaAgora() {
  return formatoHora.format(new Date());
}

/**
 * Data e hora digitadas (horário de São Paulo) para o instante ISO que a API espera.
 * São Paulo não tem horário de verão desde 2019, então o fuso é sempre UTC−3.
 * @param {string} data AAAA-MM-DD
 * @param {string} hora HH:MM
 */
export function instanteDeSaoPaulo(data, hora) {
  return new Date(`${data}T${hora}:00-03:00`).toISOString();
}

/** Número em pt-BR, com até 3 casas: 2400 → "2.400"; 2.5 → "2,5". */
export function formatarNumero(valor) {
  return formatoNumero.format(valor);
}

/** Quantidade com a unidade: (2400, "G") → "2.400 g". */
export function formatarQuantidade(valor, unidade) {
  return `${formatoNumero.format(valor)} ${UNIDADES[unidade] ?? unidade}`;
}

/**
 * Quantidade como se pesa na cozinha: a partir de 1.000 g vira kg (e 1.000 ml vira L), com precisão de 1 g;
 * abaixo disso, grama inteira (uma casa abaixo de 10 g). (16666.667, "G") → "16,667 kg"; (333.333, "G") → "333 g".
 */
export function formatarQuantidadeLegivel(valor, unidade) {
  const numero = Number(valor);
  const grande = { G: "KG", ML: "L" }[unidade];
  if (grande && Math.abs(Math.round(numero)) >= 1000) return formatarQuantidade(Math.round(numero) / 1000, grande);
  if (unidade === "G" || unidade === "ML") {
    const casas = Math.abs(numero) < 10 ? 1 : 0;
    return `${new Intl.NumberFormat("pt-BR", { maximumFractionDigits: casas }).format(numero)} ${UNIDADES[unidade]}`;
  }
  return formatarQuantidade(numero, unidade);
}
