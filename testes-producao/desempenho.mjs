#!/usr/bin/env node
/**
 * Medição de desempenho real do SmartProd na AWS (Seção 1 — RNF-D001: ≤ 1,5 s).
 *
 * Modos (--modo=...):
 *   semeadura    Cria 500 OPs (proporção pendentes / em andamento / concluídas, datas variadas) via API
 *   quente       2 aquecimentos + 30 chamadas seguidas em cada rota (min, p50, p95, p99, max)
 *   frio         1 chamada em cada rota principal (usado após cold start da Lambda ou auto-pause do Aurora)
 *   completo     Executa semeadura + quente (padrão)
 *
 * Variáveis de ambiente obrigatórias:
 *   SMARTPROD_URL            URL base da Lambda
 *   SMARTPROD_GERENTE_EMAIL  E-mail do gerente de teste
 *   SMARTPROD_GERENTE_SENHA  Senha do gerente de teste
 *   SMARTPROD_FUNC_EMAIL     E-mail do funcionário de teste
 *   SMARTPROD_FUNC_SENHA     Senha do funcionário de teste
 *
 * Opcionais:
 *   SMARTPROD_TOTAL_OPS      Quantidade de OPs para semear (padrão: 500)
 *   SMARTPROD_REPETICOES     Repetições no cenário quente (padrão: 30)
 *   SMARTPROD_OUT_JSON       Arquivo JSON para salvar as métricas
 */
import fs from 'node:fs';
import { performance } from 'node:perf_hooks';

const BASE_URL = (process.env.SMARTPROD_URL || '').replace(/\/+$/, '');
const GERENTE_EMAIL = process.env.SMARTPROD_GERENTE_EMAIL || '';
const GERENTE_SENHA = process.env.SMARTPROD_GERENTE_SENHA || '';
const FUNC_EMAIL = process.env.SMARTPROD_FUNC_EMAIL || '';
const FUNC_SENHA = process.env.SMARTPROD_FUNC_SENHA || '';
const TOTAL_OPS = Number(process.env.SMARTPROD_TOTAL_OPS || 500);
const REPETICOES = Number(process.env.SMARTPROD_REPETICOES || 30);
const OUT_JSON = process.env.SMARTPROD_OUT_JSON || '';

const argModo = process.argv.find((a) => a.startsWith('--modo='));
const MODO = argModo ? argModo.split('=')[1] : 'completo';

if (!BASE_URL || !GERENTE_EMAIL || !GERENTE_SENHA || !FUNC_EMAIL || !FUNC_SENHA) {
  console.error(
    'Defina SMARTPROD_URL, SMARTPROD_GERENTE_EMAIL, SMARTPROD_GERENTE_SENHA, SMARTPROD_FUNC_EMAIL e SMARTPROD_FUNC_SENHA.'
  );
  process.exit(2);
}

function hojeSaoPaulo() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo' }).format(new Date());
}

function percentil(valoresOrdenados, p) {
  if (!valoresOrdenados.length) return 0;
  const idx = Math.ceil((p / 100) * valoresOrdenados.length) - 1;
  return valoresOrdenados[Math.max(0, Math.min(valoresOrdenados.length - 1, idx))];
}

function resumir(amostras) {
  const ord = [...amostras].sort((a, b) => a - b);
  const soma = ord.reduce((s, v) => s + v, 0);
  const r = (v) => Math.round(v * 10) / 10;
  return {
    n: ord.length,
    min: r(ord[0]),
    media: r(soma / ord.length),
    p50: r(percentil(ord, 50)),
    p95: r(percentil(ord, 95)),
    p99: r(percentil(ord, 99)),
    max: r(ord[ord.length - 1]),
    amostras: ord.map(r),
  };
}

async function chamada(caminho, { metodo = 'GET', token = null, corpo = undefined } = {}) {
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  let body;
  if (corpo !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(corpo);
  }
  const inicio = performance.now();
  const res = await fetch(`${BASE_URL}${caminho}`, { method: metodo, headers, body });
  const texto = await res.text();
  const ms = performance.now() - inicio;
  let json = null;
  if (texto && (res.headers.get('content-type') || '').includes('json')) {
    try {
      json = JSON.parse(texto);
    } catch {
      json = null;
    }
  }
  return { status: res.status, ms, bytes: Buffer.byteLength(texto, 'utf8'), json, texto };
}

async function prepararFichaBase(tokenGerente) {
  const fichasResp = await chamada('/api/fichas-tecnicas', { token: tokenGerente });
  const lista = Array.isArray(fichasResp.json) ? fichasResp.json : [];
  let fichaResumo = lista.find((f) => /Pão francês/i.test(f.nome)) || lista[0];
  if (!fichaResumo) {
    const ins1 = await chamada('/api/insumos', {
      metodo: 'POST',
      token: tokenGerente,
      corpo: { nome: 'Farinha benchmark', unidadeMedida: 'G', custoUnitario: 0.006 },
    });
    const ins2 = await chamada('/api/insumos', {
      metodo: 'POST',
      token: tokenGerente,
      corpo: { nome: 'Água benchmark', unidadeMedida: 'ML', custoUnitario: 0.0005 },
    });
    const f = await chamada('/api/fichas-tecnicas', {
      metodo: 'POST',
      token: tokenGerente,
      corpo: {
        nome: 'Pão francês benchmark',
        rendimentoPadrao: 50,
        ingredientes: [
          { insumoId: ins1.json.id, quantidadeBase: 1000, ehFarinha: true },
          { insumoId: ins2.json.id, quantidadeBase: 600, ehFarinha: false },
        ],
        passos: [{ ordem: 1, etapa: 'Mistura', descricao: 'Misturar ingredientes.', duracaoSegundos: 60 }],
      },
    });
    fichaResumo = f.json;
  }
  const det = await chamada(`/api/fichas-tecnicas/${fichaResumo.id}`, { token: tokenGerente });
  return det.json;
}

async function semear500Ops(tokenGerente, tokenFunc, funcId, ficha) {
  const existentes = await chamada('/api/ordens-producao', { token: tokenGerente });
  const qtdAtual = Array.isArray(existentes.json) ? existentes.json.length : 0;
  const faltam = Math.max(0, TOTAL_OPS - qtdAtual);
  console.log(`OPs visíveis no Kanban hoje: ${qtdAtual}. Meta de semeadura: ${TOTAL_OPS} (criando ${faltam} novas OPs)...`);
  if (faltam === 0) return { jaExistiam: qtdAtual, criadas: 0 };

  // Proporção realista: 40% PENDENTE, 25% EM_ANDAMENTO, 35% CONCLUIDA (todas entregando hoje ou nos próximos 3 dias;
  // concluídas entregando hoje para aparecerem no Kanban padrão + dashboard).
  const qtdConcluidas = Math.round(faltam * 0.35);
  const qtdAndamento = Math.round(faltam * 0.25);
  const qtdPendentes = faltam - qtdConcluidas - qtdAndamento;

  const baseHoje = Date.now() + 30 * 60 * 1000; // +30 min (sempre hoje em America/Sao_Paulo até ~23h)
  const tarefas = [];
  for (let i = 0; i < faltam; i++) {
    let destino = 'PENDENTE';
    let offsetMs = (i % 4) * 24 * 3600 * 1000 + (i % 120) * 60 * 1000;
    if (i < qtdConcluidas) {
      destino = 'CONCLUIDA';
      offsetMs = (i % 180) * 60 * 1000; // hoje
    } else if (i < qtdConcluidas + qtdAndamento) {
      destino = 'EM_ANDAMENTO';
      offsetMs = (i % 2) * 24 * 3600 * 1000 + (i % 180) * 60 * 1000;
    }
    tarefas.push({
      indice: i,
      destino,
      horarioEntrega: new Date(baseHoje + offsetMs).toISOString(),
      quantidadeUnidades: 50 + (i % 5) * 25,
    });
  }

  const CONCORRENCIA = 10;
  let criadas = 0;
  let iniciadas = 0;
  let concluidas = 0;
  const inicio = performance.now();

  let cursor = 0;
  async function worker() {
    while (cursor < tarefas.length) {
      const t = tarefas[cursor++];
      const rCriar = await chamada('/api/ordens-producao', {
        metodo: 'POST',
        token: tokenGerente,
        corpo: {
          fichaTecnicaId: ficha.id,
          quantidadeUnidades: t.quantidadeUnidades,
          horarioEntrega: t.horarioEntrega,
          responsavelId: funcId,
        },
      });
      if (rCriar.status !== 201) {
        throw new Error(`Falha ao semear OP #${t.indice}: HTTP ${rCriar.status} ${rCriar.texto}`);
      }
      criadas++;
      const id = rCriar.json.id;
      if (t.destino === 'EM_ANDAMENTO' || t.destino === 'CONCLUIDA') {
        const rIni = await chamada(`/api/ordens-producao/${id}/iniciar`, { metodo: 'PATCH', token: tokenFunc });
        if (rIni.status !== 200) throw new Error(`Falha ao iniciar OP ${id}: HTTP ${rIni.status}`);
        iniciadas++;
      }
      if (t.destino === 'CONCLUIDA') {
        const rCon = await chamada(`/api/ordens-producao/${id}/concluir`, {
          metodo: 'PATCH',
          token: tokenFunc,
          corpo: { quantidadeProduzida: t.quantidadeUnidades },
        });
        if (rCon.status !== 200) throw new Error(`Falha ao concluir OP ${id}: HTTP ${rCon.status}`);
        concluidas++;
      }
      if (criadas % 100 === 0) {
        console.log(`  Progresso semeadura: ${criadas}/${faltam} (${Math.round((performance.now() - inicio) / 1000)} s)`);
      }
    }
  }

  await Promise.all(Array.from({ length: CONCORRENCIA }, () => worker()));
  const duracaoSeg = Math.round(((performance.now() - inicio) / 1000) * 10) / 10;
  console.log(`Semeadura concluída em ${duracaoSeg}s: criadas=${criadas}, pendentes=${qtdPendentes}, andamento=${iniciadas - concluidas}, concluidas=${concluidas}.`);
  return { jaExistiam: qtdAtual, criadas, pendentes: qtdPendentes, emAndamento: iniciadas - concluidas, concluidas, duracaoSeg };
}

async function medirRota(nome, fn, aquecimento = 2, repeticoes = REPETICOES) {
  for (let i = 0; i < aquecimento; i++) {
    const r = await fn();
    if (r.status < 200 || r.status >= 300) {
      throw new Error(`Aquecimento falhou em ${nome}: HTTP ${r.status}`);
    }
  }
  const amostras = [];
  let ultimoStatus = 0;
  let ultimoBytes = 0;
  let ultimoItens = null;
  for (let i = 0; i < repeticoes; i++) {
    const r = await fn();
    if (r.status < 200 || r.status >= 300) {
      throw new Error(`Medição falhou em ${nome} (#${i + 1}): HTTP ${r.status}`);
    }
    amostras.push(r.ms);
    ultimoStatus = r.status;
    ultimoBytes = r.bytes;
    if (Array.isArray(r.json)) ultimoItens = r.json.length;
  }
  const stats = resumir(amostras);
  const okRnf = stats.p95 <= 1500;
  console.log(
    `${nome.padEnd(52)} | min=${String(stats.min).padStart(6)} | p50=${String(stats.p50).padStart(6)} | p95=${String(stats.p95).padStart(6)} | p99=${String(stats.p99).padStart(6)} | max=${String(stats.max).padStart(6)} ms | ${okRnf ? 'OK' : 'ACIMA 1500ms'}`
  );
  return { nome, status: ultimoStatus, bytes: ultimoBytes, itens: ultimoItens, rnfD001Ok: okRnf, ...stats };
}

async function executar() {
  const loginGerente = await chamada('/api/auth/login', {
    metodo: 'POST',
    corpo: { email: GERENTE_EMAIL, senha: GERENTE_SENHA },
  });
  if (loginGerente.status !== 200) throw new Error(`Login gerente falhou: HTTP ${loginGerente.status}`);
  const tokenGerente = loginGerente.json.token;

  const loginFunc = await chamada('/api/auth/login', {
    metodo: 'POST',
    corpo: { email: FUNC_EMAIL, senha: FUNC_SENHA },
  });
  if (loginFunc.status !== 200) throw new Error(`Login funcionário falhou: HTTP ${loginFunc.status}`);
  const tokenFunc = loginFunc.json.token;
  const funcId = loginFunc.json.usuario.id;

  const ficha = await prepararFichaBase(tokenGerente);
  const estoquePayload = {
    estoque: ficha.ingredientes.map((ing, idx) => ({
      insumoId: ing.insumoId,
      quantidade: Number(ing.quantidadeBase) * (idx === 0 ? 3 : 5),
    })),
  };
  const hoje = hojeSaoPaulo();

  const saida = {
    executadoEm: new Date().toISOString(),
    modo: MODO,
    fichaId: ficha.id,
    dataReferenciaSP: hoje,
  };

  if (MODO === 'semeadura' || MODO === 'completo') {
    saida.semeadura = await semear500Ops(tokenGerente, tokenFunc, funcId, ficha);
  }

  if (MODO === 'frio') {
    const rKanban = await chamada('/api/ordens-producao', { token: tokenGerente });
    saida.frio = {
      loginMs: Math.round(loginGerente.ms * 10) / 10,
      loginStatus: loginGerente.status,
      kanbanMs: Math.round(rKanban.ms * 10) / 10,
      kanbanStatus: rKanban.status,
      kanbanItens: Array.isArray(rKanban.json) ? rKanban.json.length : null,
      passou45s: loginGerente.ms > 45000 || rKanban.ms > 45000,
    };
    console.log('MEDICAO_FRIO:', JSON.stringify(saida.frio));
  }

  if (MODO === 'quente' || MODO === 'completo') {
    console.log(`\n=== MEDIÇÃO QUENTE (2 aquecimentos + ${REPETICOES} repetições) ===`);
    const rotas = [
      ['GET /api/ordens-producao (Kanban padrão)', () => chamada('/api/ordens-producao', { token: tokenGerente })],
      ['GET /api/ordens-producao?status=PENDENTE', () => chamada('/api/ordens-producao?status=PENDENTE', { token: tokenGerente })],
      [`GET /api/ordens-producao?data=${hoje}`, () => chamada(`/api/ordens-producao?data=${hoje}`, { token: tokenGerente })],
      [
        `GET /api/ordens-producao?status=PENDENTE&data=${hoje}`,
        () => chamada(`/api/ordens-producao?status=PENDENTE&data=${hoje}`, { token: tokenGerente }),
      ],
      [
        `GET /api/fichas-tecnicas/${ficha.id}/calculo?unidades=150`,
        () => chamada(`/api/fichas-tecnicas/${ficha.id}/calculo?unidades=150`, { token: tokenFunc }),
      ],
      [
        `POST /api/fichas-tecnicas/${ficha.id}/calculo-por-estoque`,
        () => chamada(`/api/fichas-tecnicas/${ficha.id}/calculo-por-estoque`, { metodo: 'POST', token: tokenFunc, corpo: estoquePayload }),
      ],
      ['GET /api/dashboard/rendimento', () => chamada(`/api/dashboard/rendimento?data=${hoje}`, { token: tokenGerente })],
      ['GET /api/dashboard/desperdicio', () => chamada(`/api/dashboard/desperdicio?de=${hoje}&ate=${hoje}`, { token: tokenGerente })],
      ['GET /api/dashboard/ranking-desperdicio', () => chamada(`/api/dashboard/ranking-desperdicio?de=${hoje}&ate=${hoje}`, { token: tokenGerente })],
      ['GET /api/dashboard/status-ops', () => chamada('/api/dashboard/status-ops', { token: tokenGerente })],
      [
        'GET /api/dashboard/* (4 chamadas paralelas)',
        async () => {
          const t0 = performance.now();
          const resps = await Promise.all([
            chamada(`/api/dashboard/rendimento?data=${hoje}`, { token: tokenGerente }),
            chamada(`/api/dashboard/desperdicio?de=${hoje}&ate=${hoje}`, { token: tokenGerente }),
            chamada(`/api/dashboard/ranking-desperdicio?de=${hoje}&ate=${hoje}`, { token: tokenGerente }),
            chamada('/api/dashboard/status-ops', { token: tokenGerente }),
          ]);
          const ok = resps.every((r) => r.status === 200);
          return {
            status: ok ? 200 : 500,
            ms: performance.now() - t0,
            bytes: resps.reduce((s, r) => s + r.bytes, 0),
            json: null,
          };
        },
      ],
      [
        'POST /api/auth/login (BCrypt custo 12)',
        () => chamada('/api/auth/login', { metodo: 'POST', corpo: { email: FUNC_EMAIL, senha: FUNC_SENHA } }),
      ],
    ];

    saida.quente = [];
    for (const [nome, fn] of rotas) {
      saida.quente.push(await medirRota(nome, fn));
    }
  }

  if (OUT_JSON) {
    fs.writeFileSync(OUT_JSON, JSON.stringify(saida, null, 2), 'utf8');
    console.log(`\nMétricas salvas em: ${OUT_JSON}`);
  }
}

executar().catch((err) => {
  console.error('Erro fatal:', err.message);
  process.exit(1);
});
