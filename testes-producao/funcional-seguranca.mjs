#!/usr/bin/env node
/**
 * Testes funcionais, regras de banco real e verificações de segurança do SmartProd na AWS (Seção 2).
 *
 * Variáveis de ambiente obrigatórias:
 *   SMARTPROD_URL            URL base da Lambda (ex.: https://xxxx.lambda-url.us-east-2.on.aws)
 *   SMARTPROD_GERENTE_EMAIL  E-mail da conta gerente de teste
 *   SMARTPROD_GERENTE_SENHA  Senha da conta gerente de teste
 *   SMARTPROD_FUNC_EMAIL     E-mail da conta funcionário de teste
 *   SMARTPROD_FUNC_SENHA     Senha da conta funcionário de teste
 *
 * Opcional:
 *   SMARTPROD_OUT_JSON       Caminho para gravar resultado estruturado (sem segredos)
 */
import fs from 'node:fs';
import { performance } from 'node:perf_hooks';

const BASE_URL = (process.env.SMARTPROD_URL || '').replace(/\/+$/, '');
const GERENTE_EMAIL = process.env.SMARTPROD_GERENTE_EMAIL || '';
const GERENTE_SENHA = process.env.SMARTPROD_GERENTE_SENHA || '';
const FUNC_EMAIL = process.env.SMARTPROD_FUNC_EMAIL || '';
const FUNC_SENHA = process.env.SMARTPROD_FUNC_SENHA || '';
const OUT_JSON = process.env.SMARTPROD_OUT_JSON || '';

if (!BASE_URL || !GERENTE_EMAIL || !GERENTE_SENHA || !FUNC_EMAIL || !FUNC_SENHA) {
  console.error(
    'Defina SMARTPROD_URL, SMARTPROD_GERENTE_EMAIL, SMARTPROD_GERENTE_SENHA, SMARTPROD_FUNC_EMAIL e SMARTPROD_FUNC_SENHA.'
  );
  process.exit(2);
}

const SUFIXO = Date.now().toString(36);
const resultados = [];

function registrar(secao, id, titulo, passou, evidencia) {
  resultados.push({ secao, id, titulo, passou: Boolean(passou), evidencia });
  const marca = passou ? 'PASSOU' : 'FALHOU';
  console.log(`[${marca}] ${id} — ${titulo}`);
  if (evidencia) console.log(`         ${evidencia}`);
}

async function req(caminho, { metodo = 'GET', token = null, corpo = undefined, headers = {} } = {}) {
  const h = { Accept: 'application/json', ...headers };
  if (token) h.Authorization = `Bearer ${token}`;
  let body;
  if (corpo !== undefined) {
    h['Content-Type'] = 'application/json';
    body = JSON.stringify(corpo);
  }
  const inicio = performance.now();
  const res = await fetch(`${BASE_URL}${caminho}`, { method: metodo, headers: h, body });
  const ms = Math.round((performance.now() - inicio) * 10) / 10;
  const contentType = res.headers.get('content-type') || '';
  const texto = await res.text();
  let json = null;
  if (contentType.includes('json') && texto) {
    try {
      json = JSON.parse(texto);
    } catch {
      json = null;
    }
  }
  return { status: res.status, headers: res.headers, contentType, texto, json, ms };
}

function hojeSaoPaulo() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
}

function somarDiasISO(isoDate, dias) {
  const [a, m, d] = isoDate.split('-').map(Number);
  return new Date(Date.UTC(a, m - 1, d + dias)).toISOString().slice(0, 10);
}

function contemTextoFinanceiro(obj) {
  const s = JSON.stringify(obj);
  return /custoUnitario|custoReceita|custoUnidade|custoPorKg|totalReais|R\$/i.test(s);
}

async function executar() {
  const contexto = {
    executadoEm: new Date().toISOString(),
    sufixo: SUFIXO,
    entidadesTeste: {},
  };

  // --- 1. UTF-8 em HTML e cabeçalhos ---
  const paginas = ['/', '/pages/login/index.html', '/pages/kanban/index.html', '/css/base.css', '/js/core/api.js'];
  const detalhesUtf8 = [];
  let utf8Ok = true;
  for (const p of paginas) {
    const r = await req(p, { headers: { Accept: '*/*' } });
    const temCharset = /charset=utf-8/i.test(r.contentType);
    const temMojibake = /Ã§|Ã£|Ã©|Ã¡|Ãª|â†’|â—’/.test(r.texto);
    if (r.status !== 200 || !temCharset || temMojibake) utf8Ok = false;
    detalhesUtf8.push(`${p}: HTTP ${r.status}, Content-Type="${r.contentType}", mojibake=${temMojibake}`);
  }
  registrar('infra', 'INF-UTF8-PAGINAS', 'UTF-8 e ausência de mojibake nas páginas e assets', utf8Ok, detalhesUtf8.join(' | '));

  // --- 2. Login gerente e funcionário ---
  const loginGerente = await req('/api/auth/login', {
    metodo: 'POST',
    corpo: { email: GERENTE_EMAIL, senha: GERENTE_SENHA },
  });
  const loginFunc = await req('/api/auth/login', {
    metodo: 'POST',
    corpo: { email: FUNC_EMAIL, senha: FUNC_SENHA },
  });
  const tokenGerente = loginGerente.json?.token || null;
  const tokenFunc = loginFunc.json?.token || null;
  const funcId = loginFunc.json?.usuario?.id || null;
  const gerenteId = loginGerente.json?.usuario?.id || null;

  registrar(
    'fluxo',
    'API-LOGIN',
    'Login de gerente e funcionário devolve token JWT',
    loginGerente.status === 200 && Boolean(tokenGerente) && loginFunc.status === 200 && Boolean(tokenFunc),
    `gerente=HTTP ${loginGerente.status} (${loginGerente.ms} ms, perfil=${loginGerente.json?.usuario?.perfil}); ` +
      `funcionario=HTTP ${loginFunc.status} (${loginFunc.ms} ms, perfil=${loginFunc.json?.usuario?.perfil})`
  );

  if (!tokenGerente || !tokenFunc) {
    throw new Error('Falha no login inicial; impossível continuar testes funcionais.');
  }

  // --- 3. Criar insumos com acentos UTF-8 + Ficha técnica + Porcentagem do Padeiro + Custo por kg ---
  const insumosDef = [
    { chave: 'farinha', nome: `Farinha de trigo ${SUFIXO}`, unidadeMedida: 'G', custoUnitario: 0.006 },
    { chave: 'agua', nome: `Água filtrada ${SUFIXO}`, unidadeMedida: 'ML', custoUnitario: 0.0005 },
    { chave: 'fermento', nome: `Fermento biológico ${SUFIXO}`, unidadeMedida: 'G', custoUnitario: 0.08 },
    { chave: 'sal', nome: `Sal refinado ${SUFIXO}`, unidadeMedida: 'G', custoUnitario: 0.004 },
  ];
  const insumos = {};
  for (const def of insumosDef) {
    const r = await req('/api/insumos', {
      metodo: 'POST',
      token: tokenGerente,
      corpo: { nome: def.nome, unidadeMedida: def.unidadeMedida, custoUnitario: def.custoUnitario },
    });
    if (r.status !== 201) {
      throw new Error(`Falha ao criar insumo ${def.nome}: HTTP ${r.status} ${r.texto}`);
    }
    insumos[def.chave] = r.json;
  }

  // Receita: 1000g farinha (R$ 6,00), 600ml água (R$ 0,30), 20g fermento (R$ 1,60), 20g sal (R$ 0,08)
  // Massa total = 1,640 kg | Custo total = R$ 7,98 | Custo por kg = 7,98 / 1,64 = 4,8659 -> R$ 4,87 (ou 4,86585...)
  const nomeFicha = `Pão francês teste ${SUFIXO}`;
  const criarFicha = await req('/api/fichas-tecnicas', {
    metodo: 'POST',
    token: tokenGerente,
    corpo: {
      nome: nomeFicha,
      rendimentoPadrao: 20,
      ingredientes: [
        { insumoId: insumos.farinha.id, quantidadeBase: 1000, ehFarinha: true },
        { insumoId: insumos.agua.id, quantidadeBase: 600, ehFarinha: false },
        { insumoId: insumos.fermento.id, quantidadeBase: 20, ehFarinha: false },
        { insumoId: insumos.sal.id, quantidadeBase: 20, ehFarinha: false },
      ],
      passos: [
        { ordem: 1, etapa: 'Mistura', descricao: 'Misturar farinha, água filtrada e fermento biológico até homogeneizar.', duracaoSegundos: 120 },
        { ordem: 2, etapa: 'Fermentação', descricao: 'Descansar a massa coberta para fermentação.', duracaoSegundos: 60 },
        { ordem: 3, etapa: 'Forno', descricao: 'Assar até dourar a casca.', duracaoSegundos: 3 },
      ],
    },
  });
  const ficha = criarFicha.json;
  contexto.entidadesTeste.fichaId = ficha?.id;
  contexto.entidadesTeste.insumos = Object.fromEntries(Object.entries(insumos).map(([k, v]) => [k, v.id]));

  const custosResp = await req('/api/fichas-tecnicas/custos', { token: tokenGerente });
  const custoFicha = Array.isArray(custosResp.json) ? custosResp.json.find((c) => c.fichaId === ficha?.id) : null;

  const pcts = Object.fromEntries(
    (ficha?.ingredientes || []).map((i) => [i.insumoNome, Number(i.percentualPadeiro)])
  );
  const padeiroOk =
    pcts[insumos.farinha.nome] === 100 &&
    pcts[insumos.agua.nome] === 60 &&
    pcts[insumos.fermento.nome] === 2 &&
    pcts[insumos.sal.nome] === 2;
  const custoEsperadoPorKg = 7.98 / 1.64; // 4.86585...
  const custoOk =
    custoFicha &&
    Math.abs(Number(custoFicha.custoPorKg) - custoEsperadoPorKg) <= 0.02 &&
    Math.abs(Number(custoFicha.custoReceita) - 7.98) <= 0.01;

  registrar(
    'infra',
    'INF-UTF8-JSON',
    'Acentos UTF-8 preservados no ciclo JSON (Aurora → API → cliente)',
    criarFicha.status === 201 &&
      ficha?.nome === nomeFicha &&
      ficha.ingredientes.some((i) => i.insumoNome === insumos.fermento.nome) &&
      ficha.ingredientes.some((i) => i.insumoNome === insumos.agua.nome),
    `ficha.nome="${ficha?.nome}", fermento="${insumos.fermento.nome}", agua="${insumos.agua.nome}"`
  );

  registrar(
    'fluxo',
    'API-INSUMOS-FICHA',
    'Criar insumos e ficha técnica; validar Porcentagem do Padeiro e custo por kg',
    criarFicha.status === 201 && custosResp.status === 200 && padeiroOk && Boolean(custoOk),
    `HTTP ${criarFicha.status}; pcts=${JSON.stringify(Object.values(pcts))}; custoReceita=${custoFicha?.custoReceita}, custoPorKg=${custoFicha?.custoPorKg} (esperado ~${custoEsperadoPorKg.toFixed(2)})`
  );

  // --- 4. Calculadora por demanda e por estoque ---
  const calcDemanda = await req(`/api/fichas-tecnicas/${ficha.id}/calculo?unidades=100`, { token: tokenFunc });
  const farinhaDemanda = calcDemanda.json?.ingredientes?.find((i) => i.insumoId === insumos.farinha.id);
  // Estoque: 5000g farinha (5x=100 un), 3000ml água (5x=100 un), 40g fermento (2x=40 un -> LIMITANTE), 100g sal (5x=100 un)
  const calcEstoque = await req(`/api/fichas-tecnicas/${ficha.id}/calculo-por-estoque`, {
    metodo: 'POST',
    token: tokenFunc,
    corpo: {
      estoque: [
        { insumoId: insumos.farinha.id, quantidade: 5000 },
        { insumoId: insumos.agua.id, quantidade: 3000 },
        { insumoId: insumos.fermento.id, quantidade: 40 },
        { insumoId: insumos.sal.id, quantidade: 100 },
      ],
    },
  });
  const limitante = calcEstoque.json?.ingredienteLimitante;
  const calcOk =
    calcDemanda.status === 200 &&
    Number(farinhaDemanda?.quantidade) === 5000 &&
    calcEstoque.status === 200 &&
    Number(calcEstoque.json?.unidadesPossiveis) === 40 &&
    (limitante?.insumoId === insumos.fermento.id || limitante?.nome === insumos.fermento.nome || calcEstoque.texto.includes(insumos.fermento.nome));

  registrar(
    'fluxo',
    'API-CALCULADORA',
    'Calculadora por demanda (100 un) e por estoque (ingrediente limitante)',
    calcOk,
    `demanda=HTTP ${calcDemanda.status} (farinha=${farinhaDemanda?.quantidade}g); estoque=HTTP ${calcEstoque.status} (unidadesPossiveis=${calcEstoque.json?.unidadesPossiveis}, limitante=${JSON.stringify(limitante)})`
  );

  // --- 5. Fluxo completo da OP + Perda + Conclusão + Dashboard ---
  const entregaHoje = new Date(Date.now() + 2 * 3600 * 1000).toISOString();
  const abrirOp = await req('/api/ordens-producao', {
    metodo: 'POST',
    token: tokenGerente,
    corpo: {
      fichaTecnicaId: ficha.id,
      quantidadeUnidades: 50,
      horarioEntrega: entregaHoje,
      responsavelId: funcId,
    },
  });
  const opId = abrirOp.json?.id;
  const iniciarOp = await req(`/api/ordens-producao/${opId}/iniciar`, { metodo: 'PATCH', token: tokenFunc });
  const registrarPerda = await req(`/api/ordens-producao/${opId}/perdas`, {
    metodo: 'POST',
    token: tokenFunc,
    corpo: {
      etapa: 'Forno',
      pesoKg: 1.5,
      unidades: 18,
      motivo: 'Fornada queimada no teste funcional',
    },
  });
  const concluirOp = await req(`/api/ordens-producao/${opId}/concluir`, {
    metodo: 'PATCH',
    token: tokenFunc,
    corpo: { quantidadeProduzida: 45 },
  });

  const fluxoOpOk =
    abrirOp.status === 201 &&
    iniciarOp.status === 200 &&
    iniciarOp.json?.status === 'EM_ANDAMENTO' &&
    registrarPerda.status === 201 &&
    concluirOp.status === 200 &&
    concluirOp.json?.status === 'CONCLUIDA' &&
    concluirOp.json?.quantidadeProduzida === 45;

  registrar(
    'fluxo',
    'API-OP-PERDA-CONCLUIR',
    'Fluxo completo: abrir OP → iniciar → registrar perda (1,5 kg) → concluir (45 un)',
    fluxoOpOk,
    `abrir=HTTP ${abrirOp.status} (#${opId}); iniciar=HTTP ${iniciarOp.status}; perda=HTTP ${registrarPerda.status} (id=${registrarPerda.json?.id}); concluir=HTTP ${concluirOp.status} (prod=${concluirOp.json?.quantidadeProduzida})`
  );

  // Dashboard do gerente
  const hoje = hojeSaoPaulo();
  const [dashRend, dashDesp, dashRank, dashStatus] = await Promise.all([
    req(`/api/dashboard/rendimento?data=${hoje}`, { token: tokenGerente }),
    req(`/api/dashboard/desperdicio?de=${hoje}&ate=${hoje}`, { token: tokenGerente }),
    req(`/api/dashboard/ranking-desperdicio?de=${hoje}&ate=${hoje}`, { token: tokenGerente }),
    req('/api/dashboard/status-ops', { token: tokenGerente }),
  ]);
  const itemRanking = Array.isArray(dashRank.json) ? dashRank.json.find((x) => x.produto === nomeFicha) : null;
  const custoPerdaEsperado = Math.round(1.5 * Number(custoFicha.custoPorKg) * 100) / 100;
  const dashOk =
    dashRend.status === 200 &&
    Number(dashRend.json?.produzido) >= 45 &&
    dashDesp.status === 200 &&
    Number(dashDesp.json?.totalKg) >= 1.5 &&
    dashRank.status === 200 &&
    itemRanking &&
    Math.abs(Number(itemRanking.totalKg) - 1.5) < 0.001 &&
    Math.abs(Number(itemRanking.totalReais) - custoPerdaEsperado) <= 0.03 &&
    dashStatus.status === 200;

  registrar(
    'fluxo',
    'API-DASHBOARD',
    'Dashboard reflete OP concluída e perda em kg e R$ (kg × custoPorKg)',
    Boolean(dashOk),
    `rendimento=${JSON.stringify(dashRend.json)}; desperdicio=${JSON.stringify(dashDesp.json)}; rankingFicha=${JSON.stringify(itemRanking)} (esperado R$ ${custoPerdaEsperado.toFixed(2)}); statusOps=${JSON.stringify(dashStatus.json)}`
  );

  // --- 6. Cancelar OP pendente (204) vs cancelar OP em andamento (409 pt-BR) ---
  const opPend = await req('/api/ordens-producao', {
    metodo: 'POST',
    token: tokenGerente,
    corpo: { fichaTecnicaId: ficha.id, quantidadeUnidades: 20, horarioEntrega: entregaHoje, responsavelId: funcId },
  });
  const cancelPend = await req(`/api/ordens-producao/${opPend.json.id}`, { metodo: 'DELETE', token: tokenGerente });

  const opAnd = await req('/api/ordens-producao', {
    metodo: 'POST',
    token: tokenGerente,
    corpo: { fichaTecnicaId: ficha.id, quantidadeUnidades: 20, horarioEntrega: entregaHoje, responsavelId: funcId },
  });
  await req(`/api/ordens-producao/${opAnd.json.id}/iniciar`, { metodo: 'PATCH', token: tokenFunc });
  const cancelAnd = await req(`/api/ordens-producao/${opAnd.json.id}`, { metodo: 'DELETE', token: tokenGerente });

  registrar(
    'fluxo',
    'API-CANCELAR-OP',
    'Cancelar OP pendente → 204; cancelar OP em andamento → 409 em português',
    cancelPend.status === 204 && cancelAnd.status === 409 && Boolean(cancelAnd.json?.detail),
    `pendente=HTTP ${cancelPend.status}; em_andamento=HTTP ${cancelAnd.status} detail="${cancelAnd.json?.detail}"`
  );

  // --- 7. Concorrência real na conclusão da mesma OP ---
  // Dispara até 3 tentativas paralelas de conclusão (2 clientes ao mesmo tempo) para observar @Version / conflito 409 real
  let concResultado = null;
  for (let tentativa = 1; tentativa <= 3; tentativa++) {
    const opC = await req('/api/ordens-producao', {
      metodo: 'POST',
      token: tokenGerente,
      corpo: { fichaTecnicaId: ficha.id, quantidadeUnidades: 30, horarioEntrega: entregaHoje, responsavelId: funcId },
    });
    await req(`/api/ordens-producao/${opC.json.id}/iniciar`, { metodo: 'PATCH', token: tokenFunc });
    const [r1, r2] = await Promise.all([
      req(`/api/ordens-producao/${opC.json.id}/concluir`, { metodo: 'PATCH', token: tokenFunc, corpo: { quantidadeProduzida: 28 } }),
      req(`/api/ordens-producao/${opC.json.id}/concluir`, { metodo: 'PATCH', token: tokenGerente, corpo: { quantidadeProduzida: 29 } }),
    ]);
    const finalOp = await req(`/api/ordens-producao/${opC.json.id}`, { token: tokenGerente });
    const statuses = [r1.status, r2.status].sort((a, b) => a - b);
    const vencedor = r1.status === 200 ? r1 : r2.status === 200 ? r2 : null;
    const perdedor = r1.status === 409 ? r1 : r2.status === 409 ? r2 : null;
    const msgOtimista = /Outra pessoa alterou este registro ao mesmo tempo/i.test(perdedor?.json?.detail || '');
    concResultado = {
      tentativa,
      opId: opC.json.id,
      statuses,
      vencedorQtd: vencedor?.json?.quantidadeProduzida,
      salvoQtd: finalOp.json?.quantidadeProduzida,
      perdedorDetail: perdedor?.json?.detail,
      msgOtimista,
    };
    if (statuses[0] === 200 && statuses[1] === 409 && msgOtimista) break;
  }
  const concOk =
    concResultado.statuses[0] === 200 &&
    concResultado.statuses[1] === 409 &&
    concResultado.vencedorQtd === concResultado.salvoQtd;

  registrar(
    'banco-real',
    'DB-CONCORRENCIA-OP',
    'Concorrência real ao concluir mesma OP: 1×200 + 1×409, vence o valor salvo',
    concOk,
    `tentativa=${concResultado.tentativa}, statuses=${JSON.stringify(concResultado.statuses)}, salvo=${concResultado.salvoQtd} (vencedor=${concResultado.vencedorQtd}), otimistaLock=${concResultado.msgOtimista}, detail409="${concResultado.perdedorDetail}"`
  );

  // --- 8. Fuso horário America/Sao_Paulo: entrega 23:30 Brasília (02:30Z do dia seguinte em UTC) ---
  // Escolhemos um dia futuro (hoje + 2 dias) às 23:30-03:00 para não colidir com entregas de hoje nem ficar no passado
  const diaFusoSP = somarDiasISO(hoje, 2);
  const diaSeguinteUTC = somarDiasISO(diaFusoSP, 1);
  const entrega2330SP = `${diaFusoSP}T23:30:00-03:00`; // equivale a diaSeguinteUTC T02:30:00Z
  const opFuso = await req('/api/ordens-producao', {
    metodo: 'POST',
    token: tokenGerente,
    corpo: { fichaTecnicaId: ficha.id, quantidadeUnidades: 33, horarioEntrega: entrega2330SP, responsavelId: funcId },
  });
  const kanbanDiaSP = await req(`/api/ordens-producao?data=${diaFusoSP}`, { token: tokenGerente });
  const kanbanDiaSeg = await req(`/api/ordens-producao?data=${diaSeguinteUTC}`, { token: tokenGerente });
  const rendDiaSP = await req(`/api/dashboard/rendimento?data=${diaFusoSP}`, { token: tokenGerente });
  const rendDiaSeg = await req(`/api/dashboard/rendimento?data=${diaSeguinteUTC}`, { token: tokenGerente });

  const noDiaSP = Array.isArray(kanbanDiaSP.json) && kanbanDiaSP.json.some((o) => o.id === opFuso.json?.id);
  const noDiaSeg = Array.isArray(kanbanDiaSeg.json) && kanbanDiaSeg.json.some((o) => o.id === opFuso.json?.id);
  const fusoOk =
    opFuso.status === 201 &&
    noDiaSP &&
    !noDiaSeg &&
    Number(rendDiaSP.json?.previsto) >= 33 &&
    Number(rendDiaSeg.json?.previsto || 0) === 0;

  registrar(
    'banco-real',
    'DB-FUSO-BRASILIA',
    'Fuso America/Sao_Paulo: entrega 23:30 Brasília aparece no dia certo no Kanban e Dashboard',
    fusoOk,
    `entrega=${entrega2330SP} (UTC=${opFuso.json?.horarioEntrega}); kanban[${diaFusoSP}].temOP=${noDiaSP}; kanban[${diaSeguinteUTC}].temOP=${noDiaSeg}; rendimento[${diaFusoSP}].previsto=${rendDiaSP.json?.previsto}; rendimento[${diaSeguinteUTC}].previsto=${rendDiaSeg.json?.previsto}`
  );
  // Cancela OP futura de fuso para manter banco limpo
  if (opFuso.json?.id) {
    await req(`/api/ordens-producao/${opFuso.json.id}`, { metodo: 'DELETE', token: tokenGerente });
  }

  // --- 9. Cadastro concorrente de dois usuários com mesmo e-mail: 1×201 e 1×409 ---
  const emailDup = `teste.concorrente.${SUFIXO}@smartprod.test`;
  const [u1, u2] = await Promise.all([
    req('/api/usuarios', {
      metodo: 'POST',
      token: tokenGerente,
      corpo: { nome: 'Usuário Concorrente A', email: emailDup, senha: 'SenhaTeste#2026', perfil: 'FUNCIONARIO' },
    }),
    req('/api/usuarios', {
      metodo: 'POST',
      token: tokenGerente,
      corpo: { nome: 'Usuário Concorrente B', email: emailDup, senha: 'SenhaTeste#2026', perfil: 'FUNCIONARIO' },
    }),
  ]);
  const stDup = [u1.status, u2.status].sort((a, b) => a - b);
  const detailDup = (u1.status === 409 ? u1 : u2).json?.detail;
  registrar(
    'banco-real',
    'DB-EMAIL-CONCORRENTE',
    'Cadastro concorrente de mesmo e-mail: 1×201 e 1×409',
    stDup[0] === 201 && stDup[1] === 409,
    `statuses=${JSON.stringify(stDup)}, detail409="${detailDup}"`
  );

  // --- 10. Segurança: sem token → 401 em todas as rotas /api/** exceto login ---
  const rotasProtegidas = [
    ['GET', '/api/insumos'],
    ['GET', '/api/fichas-tecnicas'],
    ['GET', '/api/fichas-tecnicas/custos'],
    ['GET', '/api/ordens-producao'],
    ['GET', '/api/usuarios'],
    ['GET', '/api/dashboard/rendimento'],
    ['GET', '/api/dashboard/desperdicio'],
    ['GET', '/api/dashboard/ranking-desperdicio'],
    ['GET', '/api/dashboard/status-ops'],
    ['POST', '/api/insumos'],
    ['POST', '/api/ordens-producao'],
  ];
  const respSemToken = [];
  let semTokenOk = true;
  for (const [m, c] of rotasProtegidas) {
    const r = await req(c, { metodo: m, corpo: m === 'POST' ? {} : undefined });
    if (r.status !== 401) semTokenOk = false;
    respSemToken.push(`${m} ${c}=${r.status}`);
  }
  registrar('seguranca', 'SEC-SEM-TOKEN-401', 'Sem token: 401 em todas as rotas /api/** exceto login', semTokenOk, respSemToken.join(', '));

  // --- 11. Segurança: Funcionário → 403 nas rotas de gerente ---
  const rotasGerente = [
    ['GET', '/api/usuarios'],
    ['POST', '/api/usuarios', { nome: 'X', email: `x.${SUFIXO}@smartprod.test`, senha: 'SenhaTeste#2026', perfil: 'FUNCIONARIO' }],
    ['POST', '/api/insumos', { nome: 'X', unidadeMedida: 'G', custoUnitario: 1 }],
    ['GET', '/api/fichas-tecnicas/custos'],
    ['POST', '/api/ordens-producao', { fichaTecnicaId: ficha.id, quantidadeUnidades: 10, horarioEntrega: entregaHoje, responsavelId: funcId }],
    ['GET', '/api/dashboard/rendimento'],
    ['GET', '/api/dashboard/desperdicio'],
    ['GET', '/api/dashboard/ranking-desperdicio'],
    ['GET', '/api/dashboard/status-ops'],
  ];
  const respFunc403 = [];
  let func403Ok = true;
  for (const [m, c, b] of rotasGerente) {
    const r = await req(c, { metodo: m, token: tokenFunc, corpo: b });
    if (r.status !== 403) func403Ok = false;
    respFunc403.push(`${m} ${c}=${r.status}`);
  }
  registrar('seguranca', 'SEC-FUNC-403', 'Funcionário recebe 403 em todas as rotas de gerente', func403Ok, respFunc403.join(', '));

  // --- 12. Segurança: Funcionário sem custoUnitario nem valores em R$ ---
  const [insFunc, insDetFunc, fichasFunc, fichaDetFunc, execFunc] = await Promise.all([
    req('/api/insumos', { token: tokenFunc }),
    req(`/api/insumos/${insumos.farinha.id}`, { token: tokenFunc }),
    req('/api/fichas-tecnicas', { token: tokenFunc }),
    req(`/api/fichas-tecnicas/${ficha.id}`, { token: tokenFunc }),
    req(`/api/ordens-producao/${opId}/execucao`, { token: tokenFunc }),
  ]);
  const vazamentos = [];
  for (const [rotulo, r] of [
    ['GET /api/insumos', insFunc],
    ['GET /api/insumos/{id}', insDetFunc],
    ['GET /api/fichas-tecnicas', fichasFunc],
    ['GET /api/fichas-tecnicas/{id}', fichaDetFunc],
    ['GET /api/ordens-producao/{id}/execucao', execFunc],
  ]) {
    if (r.status !== 200 || contemTextoFinanceiro(r.json)) {
      vazamentos.push(`${rotulo}(HTTP ${r.status}, vazou=${contemTextoFinanceiro(r.json)})`);
    }
  }
  registrar(
    'seguranca',
    'SEC-FUNC-SEM-CUSTOS',
    'Funcionário não recebe custoUnitario nem valores em R$ (RNF-S002)',
    vazamentos.length === 0,
    vazamentos.length === 0 ? '5 endpoints validados (HTTP 200), 0 campos financeiros expostos' : `Falhas: ${vazamentos.join('; ')}`
  );

  // --- 13. Segurança: Usuário inativado perde acesso na hora (401) ---
  const emailInat = `teste.inativar.${SUFIXO}@smartprod.test`;
  const senhaInat = 'SenhaInat#2026';
  const criarInat = await req('/api/usuarios', {
    metodo: 'POST',
    token: tokenGerente,
    corpo: { nome: 'Usuário Inativar Teste', email: emailInat, senha: senhaInat, perfil: 'FUNCIONARIO' },
  });
  const loginInat = await req('/api/auth/login', { metodo: 'POST', corpo: { email: emailInat, senha: senhaInat } });
  const antesInat = await req('/api/insumos', { token: loginInat.json?.token });
  const inativar = await req(`/api/usuarios/${criarInat.json?.id}/inativar`, { metodo: 'PATCH', token: tokenGerente });
  const depoisInat = await req('/api/insumos', { token: loginInat.json?.token });

  registrar(
    'seguranca',
    'SEC-USUARIO-INATIVADO',
    'Usuário inativado tem token rejeitado com 401 imediatamente',
    antesInat.status === 200 && inativar.status === 200 && depoisInat.status === 401,
    `antes=HTTP ${antesInat.status}; inativar=HTTP ${inativar.status}; depois=HTTP ${depoisInat.status} detail="${depoisInat.json?.detail}"`
  );

  // --- 14. Segurança: Erros sem stack trace (RFC 7807 em português) ---
  const errosCheck = [
    await req('/api/insumos/999999999', { token: tokenGerente }),
    await req('/api/insumos/nao-numerico', { token: tokenGerente }),
    await req('/api/insumos', { metodo: 'POST', token: tokenGerente, headers: { 'Content-Type': 'application/json' }, corpo: { nome: '' } }),
    await req('/api/ordens-producao/999999999', { token: tokenGerente }),
  ];
  const temStackTrace = errosCheck.some((r) => /Exception|at br\.com\.|java\.lang\.|org\.springframework\.|stackTrace/i.test(r.texto));
  const todosProblemJson = errosCheck.every((r) => /application\/problem\+json/i.test(r.contentType));
  registrar(
    'seguranca',
    'SEC-SEM-STACKTRACE',
    'Respostas de erro em RFC 7807 (português), sem stack trace',
    !temStackTrace && todosProblemJson,
    `statuses=${JSON.stringify(errosCheck.map((r) => r.status))}, problem+json=${todosProblemJson}, stackTrace=${temStackTrace}`
  );

  // --- 15. Estado atual de controles pós-correção (medição fiel sem alterar produção) ---
  const headRaiz = await req('/', { metodo: 'HEAD', headers: { Accept: '*/*' } });
  const csp = headRaiz.headers.get('content-security-policy');
  const hsts = headRaiz.headers.get('strict-transport-security');
  const xfo = headRaiz.headers.get('x-frame-options');
  const xcto = headRaiz.headers.get('x-content-type-options');
  registrar(
    'seguranca-estado-atual',
    'SEC-HEADERS-CSP-HSTS',
    'Estado atual cabeçalhos HTTP (CSP, HSTS, X-Frame-Options, X-Content-Type-Options)',
    Boolean(csp && hsts),
    `CSP=${csp ?? 'AUSENTE'}; HSTS=${hsts ?? 'AUSENTE'}; X-Frame-Options=${xfo ?? 'AUSENTE'}; X-Content-Type-Options=${xcto ?? 'AUSENTE'}`
  );

  // Brute force check (6 tentativas inválidas em conta inexistente de teste)
  const bruteStatuses = [];
  for (let i = 0; i < 6; i++) {
    const r = await req('/api/auth/login', {
      metodo: 'POST',
      corpo: { email: `inexistente.${SUFIXO}@smartprod.test`, senha: 'SenhaErrada#123' },
    });
    bruteStatuses.push(r.status);
  }
  const teve429 = bruteStatuses.includes(429);
  registrar(
    'seguranca-estado-atual',
    'SEC-RATE-LIMIT-429',
    'Estado atual rate limiting no login (6 falhas seguidas → espera 429 pós-correção)',
    teve429,
    `statuses=${JSON.stringify(bruteStatuses)} (${teve429 ? '429 presente' : 'sem rate limit ainda — todas 401'})`
  );

  // CORS check para http://localhost:5500
  const corsCheck = await req('/api/auth/login', {
    metodo: 'OPTIONS',
    headers: {
      Origin: 'http://localhost:5500',
      'Access-Control-Request-Method': 'POST',
      'Access-Control-Request-Headers': 'Content-Type',
    },
  });
  const acao = corsCheck.headers.get('access-control-allow-origin');
  const corsRecusou = acao !== 'http://localhost:5500' && acao !== '*';
  registrar(
    'seguranca-estado-atual',
    'SEC-CORS-LOCALHOST',
    'Estado atual CORS para Origin http://localhost:5500 (espera recusa pós-correção)',
    corsRecusou,
    `OPTIONS HTTP ${corsCheck.status}, Access-Control-Allow-Origin=${acao ?? 'AUSENTE'}`
  );

  const resumo = {
    ...contexto,
    total: resultados.length,
    passaram: resultados.filter((r) => r.passou).length,
    falharam: resultados.filter((r) => !r.passou).length,
    resultados,
  };

  console.log('\n--- RESUMO FUNCIONAL E SEGURANÇA ---');
  console.log(`Total: ${resumo.total} | Passaram: ${resumo.passaram} | Falharam (incl. estado pré-correção): ${resumo.falharam}`);
  if (OUT_JSON) {
    fs.writeFileSync(OUT_JSON, JSON.stringify(resumo, null, 2), 'utf8');
    console.log(`Resultado salvo em: ${OUT_JSON}`);
  }
}

executar().catch((err) => {
  console.error('Erro fatal:', err.message);
  process.exit(1);
});
