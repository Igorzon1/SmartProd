/**
 * Testes E2E de frontend no navegador real (Playwright, viewport tablet 1180x820) — Seção 3 e carga do Kanban.
 *
 * Variáveis de ambiente obrigatórias:
 *   SMARTPROD_URL            URL base da Lambda
 *   SMARTPROD_GERENTE_EMAIL  E-mail do gerente de teste
 *   SMARTPROD_GERENTE_SENHA  Senha do gerente de teste
 *   SMARTPROD_FUNC_EMAIL     E-mail do funcionário de teste
 *   SMARTPROD_FUNC_SENHA     Senha do funcionário de teste
 *
 * Opcionais:
 *   SMARTPROD_SCREENSHOT_DIR Pasta para salvar capturas de tela em tablet
 *   SMARTPROD_E2E_OUT_JSON   Caminho para salvar relatório JSON estruturado (auditoria 48px, cliques, tempos)
 *   SMARTPROD_MODO_FRIO      "1" para rodar somente medição de carga fria do Kanban em tablet
 */
import fs from 'node:fs';
import path from 'node:path';
import { performance } from 'node:perf_hooks';
import { test, expect, Page } from '@playwright/test';

const BASE_URL = (process.env.SMARTPROD_URL || '').replace(/\/+$/, '');
const GERENTE_EMAIL = process.env.SMARTPROD_GERENTE_EMAIL || '';
const GERENTE_SENHA = process.env.SMARTPROD_GERENTE_SENHA || '';
const FUNC_EMAIL = process.env.SMARTPROD_FUNC_EMAIL || '';
const FUNC_SENHA = process.env.SMARTPROD_FUNC_SENHA || '';
const SCREENSHOT_DIR = process.env.SMARTPROD_SCREENSHOT_DIR || path.resolve('resultados', 'screenshots');
const OUT_JSON = process.env.SMARTPROD_E2E_OUT_JSON || '';
const MODO_FRIO = process.env.SMARTPROD_MODO_FRIO === '1';

fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });

interface ControleMenor {
  tag: string;
  type: string;
  id: string;
  classe: string;
  texto: string;
  largura: number;
  altura: number;
  labelPaiLargura: number | null;
  labelPaiAltura: number | null;
}

interface AuditoriaTela {
  tela: string;
  totalControlesVisiveis: number;
  abaixo48px: ControleMenor[];
}

const relatorio = {
  executadoEm: new Date().toISOString(),
  viewport: { width: 1180, height: 820 },
  errosConsole: [] as Array<{ tela: string; tipo: string; texto: string }>,
  recursosHttpErro: [] as Array<{ tela: string; status: number; url: string }>,
  cargaKanbanNavegacaoMs: [] as number[],
  cargaKanbanAtualizarMs: [] as number[],
  cargaKanbanFrioMs: null as number | null,
  auditoria48px: [] as AuditoriaTela[],
  contagemToques: {} as Record<string, { toques: number; passos: string[]; atendeRnfU002: boolean }>,
  verificacoes: {} as Record<string, { passou: boolean; detalhe: string }>,
  screenshots: [] as string[],
};

function salvarRelatorio() {
  if (OUT_JSON) {
    fs.mkdirSync(path.dirname(OUT_JSON), { recursive: true });
    fs.writeFileSync(OUT_JSON, JSON.stringify(relatorio, null, 2), 'utf8');
  }
}

async function apiJson(caminho: string, token: string | null, metodo = 'GET', corpo?: unknown) {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (corpo !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(`${BASE_URL}${caminho}`, {
    method: metodo,
    headers,
    body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
  });
  const txt = await res.text();
  return { status: res.status, json: txt ? JSON.parse(txt) : null };
}

async function silenciarTutorial(page: Page) {
  const btnFechar = page
    .locator(
      'dialog[open] button:has-text("Explorar sozinho"), dialog[open] button:has-text("Agora não"), dialog[open] button:has-text("Pular"), dialog[open] button:has-text("Fechar"), .tutorial button:has-text("Sair")'
    )
    .first();
  if (await btnFechar.isVisible().catch(() => false)) {
    await btnFechar.click();
  }
}

async function fazerLogin(page: Page, email: string, senha: string) {
  await page.goto('/pages/login/index.html', { waitUntil: 'load' });
  await page.fill('input[name="email"]', email);
  await page.fill('input[name="senha"]', senha);
  await Promise.all([
    page.waitForURL((u) => !u.pathname.includes('/pages/login/'), { timeout: 45_000 }),
    page.click('#entrar'),
  ]);
  await page.waitForLoadState('load');
  await expect(page.locator('#menu .menu__link').first()).toBeVisible({ timeout: 20_000 });
}

async function auditarAlvos48px(page: Page, nomeTela: string) {
  const resultado = await page.evaluate(() => {
    const seletores = 'button, a.btn, input:not([type="hidden"]), select, textarea, nav a, nav button, label.filtro';
    const els = Array.from(document.querySelectorAll(seletores));
    let visiveis = 0;
    const menores: ControleMenor[] = [];

    for (const el of els) {
      const style = window.getComputedStyle(el);
      if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') continue;
      if ((el as HTMLElement).hidden || el.closest('[hidden]')) continue;
      const rect = el.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) continue;
      visiveis++;
      const w = Math.round(rect.width * 10) / 10;
      const h = Math.round(rect.height * 10) / 10;
      if (w < 48 || h < 48) {
        const labelPai = el.closest('label');
        const lRect = labelPai ? labelPai.getBoundingClientRect() : null;
        const texto = (
          el.textContent ||
          el.getAttribute('aria-label') ||
          el.getAttribute('name') ||
          el.getAttribute('placeholder') ||
          labelPai?.textContent ||
          ''
        )
          .replace(/\s+/g, ' ')
          .trim()
          .slice(0, 60);
        menores.push({
          tag: el.tagName.toLowerCase(),
          type: el.getAttribute('type') || '',
          id: el.id || '',
          classe: (el.className && typeof el.className === 'string' ? el.className : '').trim().slice(0, 60),
          texto,
          largura: w,
          altura: h,
          labelPaiLargura: lRect ? Math.round(lRect.width * 10) / 10 : null,
          labelPaiAltura: lRect ? Math.round(lRect.height * 10) / 10 : null,
        });
      }
    }
    return { visiveis, menores };
  });

  relatorio.auditoria48px.push({
    tela: nomeTela,
    totalControlesVisiveis: resultado.visiveis,
    abaixo48px: resultado.menores,
  });
}

async function capturar(page: Page, nomeArquivo: string, fullPage = true) {
  const caminho = path.join(SCREENSHOT_DIR, nomeArquivo);
  await page.screenshot({ path: caminho, fullPage });
  relatorio.screenshots.push(caminho);
}

function monitorarErros(page: Page, getTela: () => string) {
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      relatorio.errosConsole.push({ tela: getTela(), tipo: 'console.error', texto: msg.text() });
    }
  });
  page.on('pageerror', (err) => {
    relatorio.errosConsole.push({ tela: getTela(), tipo: 'pageerror', texto: String(err) });
  });
  page.on('response', (resp) => {
    if (resp.status() >= 400) {
      relatorio.recursosHttpErro.push({ tela: getTela(), status: resp.status(), url: new URL(resp.url()).pathname });
    }
  });
}

test.describe('SmartProd — E2E Tablet (1180x820)', () => {
  test.afterAll(() => {
    salvarRelatorio();
  });

  if (MODO_FRIO) {
    test('Carga fria do Kanban no navegador (tablet 1180x820)', async ({ page }) => {
      // Pré-autentica com token recebido por variável ou login e mede a navegação fria até o Kanban exibir cartões
      let token = process.env.SMARTPROD_TOKEN_PRE || '';
      let usuarioJson = process.env.SMARTPROD_USUARIO_PRE || '';
      if (!token || !usuarioJson) {
        const l = await apiJson('/api/auth/login', null, 'POST', { email: FUNC_EMAIL, senha: FUNC_SENHA });
        token = l.json.token;
        usuarioJson = JSON.stringify(l.json.usuario);
      }
      // Injeta sessionStorage antes do script da página rodar para que a PRIMEIRA requisição ao servidor seja o próprio Kanban frio
      await page.addInitScript(
        ([t, u]) => {
          sessionStorage.setItem('smartprod.sessao', JSON.stringify({ token: t, usuario: JSON.parse(u) }));
        },
        [token, usuarioJson]
      );
      const t0 = performance.now();
      await page.goto('/pages/kanban/index.html', { waitUntil: 'domcontentloaded', timeout: 60_000 });
      await page.locator('.op').first().waitFor({ state: 'visible', timeout: 60_000 });
      await expect(page.locator('#atualizado')).toContainText('Atualizado às', { timeout: 60_000 });
      const ms = Math.round((performance.now() - t0) * 10) / 10;
      relatorio.cargaKanbanFrioMs = ms;
      console.log(`KANBAN_PLAYWRIGHT_FRIO_MS=${ms}`);
    });
    return;
  }

  test('Fluxo completo Gerente + Funcionário (tablet 1180x820, RNF-C002, RNF-U001, RNF-U002, pt-BR)', async ({ browser }) => {
    // --- PARTE A: GERENTE ---
    const ctxGerente = await browser.newContext({
      baseURL: BASE_URL,
      viewport: { width: 1180, height: 820 },
      locale: 'pt-BR',
      timezoneId: 'America/Sao_Paulo',
    });
    const pageG = await ctxGerente.newPage();
    let telaAtual = 'login';
    monitorarErros(pageG, () => telaAtual);

    await pageG.goto('/pages/login/index.html', { waitUntil: 'load' });
    await expect(pageG.locator('#entrar')).toBeVisible();
    await auditarAlvos48px(pageG, 'Login');
    await capturar(pageG, '01-login-tablet.png', false);

    await fazerLogin(pageG, GERENTE_EMAIL, GERENTE_SENHA);
    telaAtual = 'inicio-gerente';
    await silenciarTutorial(pageG);

    const telasGerente: Array<[string, string, string | null, boolean]> = [
      ['dashboard', '/pages/dashboard/index.html', '02-dashboard-tablet.png', true],
      ['kanban-gerente', '/pages/kanban/index.html', '03-kanban-tablet.png', false],
      ['ordens-producao', '/pages/ordens-producao/index.html', '04-ordens-producao-tablet.png', false],
      ['calculadora-gerente', '/pages/calculadora/index.html', '05-calculadora-tablet.png', true],
      ['fichas-tecnicas', '/pages/fichas-tecnicas/index.html', '06-fichas-tecnicas-tablet.png', true],
      ['insumos', '/pages/insumos/index.html', null, false],
      ['usuarios', '/pages/usuarios/index.html', null, false],
    ];

    for (const [nome, url, print, full] of telasGerente) {
      telaAtual = nome;
      await pageG.goto(url, { waitUntil: 'load' });
      await expect(pageG.locator('#menu .menu__link').first()).toBeVisible({ timeout: 15_000 });
      await silenciarTutorial(pageG);

      if (nome === 'dashboard') {
        await expect(pageG.locator('#c-desperdicio')).not.toContainText('Carregando');
        await expect(pageG.locator('#c-status')).not.toContainText('Carregando');
        const textoDash = await pageG.locator('main').innerText();
        const temDataBR = /\d{2}\/\d{2}\/\d{4}/.test(textoDash);
        const temReaisBR = /R\$\s*\d+,\d{2}/.test(textoDash) || textoDash.includes('—');
        const temKgBR = /\d+(,\d+)?\s*kg/.test(textoDash);
        relatorio.verificacoes.formatacaoPtBrDashboard = {
          passou: temDataBR && temReaisBR && temKgBR,
          detalhe: `dataBR=${temDataBR}, reaisBR=${temReaisBR}, kgBR=${temKgBR}`,
        };
        expect(temDataBR).toBeTruthy();
        expect(temKgBR).toBeTruthy();
      }
      if (nome === 'kanban-gerente') {
        await expect(pageG.locator('#atualizado')).toContainText('Atualizado às');
      }
      if (nome === 'ordens-producao' || nome === 'insumos' || nome === 'usuarios') {
        await expect(pageG.locator('#contagem')).not.toContainText('Carregando');
      }
      if (nome === 'calculadora-gerente') {
        await expect(pageG.locator('#c-ficha option').first()).toBeAttached({ timeout: 15_000 });
      }
      if (nome === 'fichas-tecnicas') {
        await pageG.waitForTimeout(800);
      }
      if (print) await capturar(pageG, print, full);
    }
    await ctxGerente.close();

    // --- PARTE B: FUNCIONÁRIO ---
    // Prepara ficha dedicada (passo 1 = 60s, passo 2 = 2s) + OP pendente para testar fluxo real
    const loginG = await apiJson('/api/auth/login', null, 'POST', { email: GERENTE_EMAIL, senha: GERENTE_SENHA });
    const loginF = await apiJson('/api/auth/login', null, 'POST', { email: FUNC_EMAIL, senha: FUNC_SENHA });
    const tokenG = loginG.json.token;
    const funcId = loginF.json.usuario.id;
    const tag = Date.now().toString(36);

    const insFarinha = await apiJson('/api/insumos', tokenG, 'POST', {
      nome: `Farinha E2E ${tag}`,
      unidadeMedida: 'G',
      custoUnitario: 0.006,
    });
    const insAgua = await apiJson('/api/insumos', tokenG, 'POST', {
      nome: `Água E2E ${tag}`,
      unidadeMedida: 'ML',
      custoUnitario: 0.0005,
    });
    const fichaE2E = await apiJson('/api/fichas-tecnicas', tokenG, 'POST', {
      nome: `Pão E2E ${tag}`,
      rendimentoPadrao: 10,
      ingredientes: [
        { insumoId: insFarinha.json.id, quantidadeBase: 1000, ehFarinha: true },
        { insumoId: insAgua.json.id, quantidadeBase: 500, ehFarinha: false },
      ],
      passos: [
        { ordem: 1, etapa: 'Sova', descricao: 'Sovar até ponto de véu.', duracaoSegundos: 60 },
        { ordem: 2, etapa: 'Descanso rápido', descricao: 'Descansar 2 segundos para validar o alarme.', duracaoSegundos: 2 },
      ],
    });
    const entrega = new Date(Date.now() + 2 * 3600 * 1000).toISOString();
    const opE2E = await apiJson('/api/ordens-producao', tokenG, 'POST', {
      fichaTecnicaId: fichaE2E.json.id,
      quantidadeUnidades: 10,
      horarioEntrega: entrega,
      responsavelId: funcId,
    });
    const opId = opE2E.json.id;

    const ctxFunc = await browser.newContext({
      baseURL: BASE_URL,
      viewport: { width: 1180, height: 820 },
      locale: 'pt-BR',
      timezoneId: 'America/Sao_Paulo',
    });
    const page = await ctxFunc.newPage();
    telaAtual = 'login-funcionario';
    monitorarErros(page, () => telaAtual);

    await fazerLogin(page, FUNC_EMAIL, FUNC_SENHA);
    telaAtual = 'inicio-funcionario';
    // Audita com o modal de boas-vindas aberto (para registrar a altura real dele) e depois fecha
    await auditarAlvos48px(page, 'Início + Modal Boas-vindas (Funcionário)');
    await silenciarTutorial(page);

    // Medir carga completa do Kanban (navegação + renderização dos cartões com ~500 OPs) — 3× quente
    for (let i = 0; i < 3; i++) {
      telaAtual = `kanban-carga-${i + 1}`;
      const t0 = performance.now();
      await page.goto('/pages/kanban/index.html', { waitUntil: 'domcontentloaded' });
      await page.locator(`#op-${opId}`).waitFor({ state: 'visible', timeout: 30_000 });
      await expect(page.locator('#atualizado')).toContainText('Atualizado às');
      relatorio.cargaKanbanNavegacaoMs.push(Math.round((performance.now() - t0) * 10) / 10);
    }

    // Medir recarga do Kanban pelo botão "Atualizar" (RNF-D001) — 3× quente
    for (let i = 0; i < 3; i++) {
      const t0 = performance.now();
      await Promise.all([
        page.waitForResponse((r) => r.url().includes('/api/ordens-producao') && r.status() === 200),
        page.click('#atualizar'),
      ]);
      await expect(page.locator('#atualizar')).toBeEnabled();
      relatorio.cargaKanbanAtualizarMs.push(Math.round((performance.now() - t0) * 10) / 10);
    }

    await silenciarTutorial(page);
    await auditarAlvos48px(page, 'Kanban (Funcionário)');

    const textoCartao = await page.locator(`#op-${opId}`).innerText();
    relatorio.verificacoes.horarioBrasiliaKanban = {
      passou: /\d{2}:\d{2}/.test(textoCartao),
      detalhe: textoCartao.replace(/\s+/g, ' ').slice(0, 140),
    };

    // RNF-U002: Iniciar OP (1 toque)
    await page.locator(`#op-${opId} button:has-text("Iniciar")`).click();
    await expect(page.locator(`#c-EM_ANDAMENTO #op-${opId}`)).toBeVisible();
    relatorio.contagemToques.iniciarOp = {
      toques: 1,
      passos: ['1. Tocar em "Iniciar" no cartão da OP no Kanban'],
      atendeRnfU002: true,
    };

    // Abrir Execução / Passo a passo
    telaAtual = 'execucao';
    await page.locator(`#op-${opId} a:has-text("Passo a passo")`).click();
    await page.waitForURL(new RegExp(`execucao/index\\.html\\?id=${opId}`));
    await expect(page.locator('#titulo')).toContainText(`Pão E2E ${tag}`);
    await silenciarTutorial(page);
    await auditarAlvos48px(page, 'Execução / Passo a passo (Funcionário)');

    // Marcar ingrediente, iniciar timer (60s), esperar 2s, avançar passo (1 toque)
    const primeiroCheck = page.locator('#ingredientes input[type="checkbox"]').first();
    await primeiroCheck.check();
    await expect(primeiroCheck).toBeChecked();
    await page.click('#timer-iniciar');
    await page.waitForTimeout(2100);
    const tempoAntes = (await page.locator('#tempo').innerText()).trim();

    await page.click('#proximo');
    await expect(page.locator('#passo-numero')).toHaveText('Passo 2 de 2');
    relatorio.contagemToques.avancarPasso = {
      toques: 1,
      passos: ['1. Tocar em "Próximo passo →"'],
      atendeRnfU002: true,
    };
    await capturar(page, '07-execucao-passo-a-passo-tablet.png', true);

    // Recarregar página e verificar RNF-C002
    await page.reload({ waitUntil: 'load' });
    await expect(page.locator('#passo-numero')).toHaveText('Passo 2 de 2');
    await expect(page.locator('#ingredientes input[type="checkbox"]').first()).toBeChecked();
    await page.click('#anterior');
    await expect(page.locator('#passo-numero')).toHaveText('Passo 1 de 2');
    const tempoDepois = (await page.locator('#tempo').innerText()).trim();
    const rodando = await page.locator('#timer-pausar').isVisible();
    const [m1, s1] = tempoAntes.split(':').map(Number);
    const [m2, s2] = tempoDepois.split(':').map(Number);
    relatorio.verificacoes.persistenciaReloadRnfC002 = {
      passou: rodando && m2 * 60 + s2 <= m1 * 60 + s1 && m2 * 60 + s2 < 60,
      detalhe: `passo=2 mantido após reload, checkbox mantido, timer passo 1: antes=${tempoAntes} → após reload=${tempoDepois} (rodando=${rodando})`,
    };
    expect(rodando).toBeTruthy();

    // Alarme ao zerar temporizador (passo 2 = 2s)
    await page.click('#proximo');
    await page.click('#timer-iniciar');
    await expect(page.locator('#alarme')).toBeVisible({ timeout: 8_000 });
    const textoAlarme = (await page.locator('#alarme-texto').innerText()).trim();
    await capturar(page, '08-execucao-alarme-tablet.png', false);
    await page.click('#alarme-ok');
    await expect(page.locator('#alarme')).toBeHidden();
    relatorio.verificacoes.alarmeTemporizadorZero = {
      passou: /Tempo esgotado/i.test(textoAlarme),
      detalhe: textoAlarme,
    };

    // Registrar perda pela tela (peso sugerido + sem R$) — 2 toques
    await page.click('#registrar-perda');
    await expect(page.locator('#dialogo-perda')).toBeVisible();
    await auditarAlvos48px(page, 'Modal Registrar perda (Funcionário)');
    await page.fill('#form-perda input[name="unidades"]', '2');
    const pesoSug = await page.locator('#form-perda input[name="pesoKg"]').inputValue();
    await page.fill('#form-perda input[name="motivo"]', 'Fornada queimada teste E2E');
    await page.click('#salvar-perda');
    await expect(page.locator('#dialogo-perda')).toBeHidden();
    await expect(page.locator('#perdas')).toContainText('0,3 kg');
    const textoExec = await page.locator('main').innerText();
    const temReaisFunc = /R\$/.test(textoExec);
    relatorio.verificacoes.perdaPesoSugeridoESemReais = {
      passou: pesoSug === '0.3' && !temReaisFunc && textoExec.includes('0,3 kg'),
      detalhe: `pesoSugerido(2 un)=${pesoSug} kg (esperado 0.3), tabela exibiu "0,3 kg", R$ na tela do funcionário=${temReaisFunc}`,
    };
    relatorio.contagemToques.registrarPerda = {
      toques: 2,
      passos: ['1. Tocar em "Registrar perda"', '2. Preencher unidades/motivo e tocar em "Registrar"'],
      atendeRnfU002: true,
    };
    expect(pesoSug).toBe('0.3');
    expect(temReaisFunc).toBeFalsy();

    // Concluir OP — 2 toques
    await page.click('#finalizar');
    await expect(page.locator('#dialogo-concluir')).toBeVisible();
    await auditarAlvos48px(page, 'Modal Concluir OP (Funcionário)');
    await page.click('#confirmar-concluir');
    await expect(page.locator('#dialogo-concluir')).toBeHidden();
    await expect(page.locator('#situacao')).toHaveText('Concluída');
    relatorio.contagemToques.concluirOp = {
      toques: 2,
      passos: ['1. Tocar em "Concluir OP" no último passo', '2. Confirmar quantidade prevista e tocar em "Concluir OP"'],
      atendeRnfU002: true,
    };

    // Calculadora (Funcionário)
    telaAtual = 'calculadora-funcionario';
    await page.goto('/pages/calculadora/index.html', { waitUntil: 'load' });
    await expect(page.locator('#c-ficha option').first()).toBeAttached({ timeout: 15_000 });
    await silenciarTutorial(page);
    await auditarAlvos48px(page, 'Calculadora (Funcionário)');
    const textoCalc = await page.locator('main').innerText();
    relatorio.verificacoes.calculadoraSemReaisFuncionario = {
      passou: !/R\$/.test(textoCalc),
      detalhe: `R$ na calculadora do funcionário=${/R\$/.test(textoCalc)}`,
    };

    await ctxFunc.close();

    // Verifica erros de JS (pageerror) e recursos >= 400 que NÃO sejam /favicon.ico
    const errosReaisHttp = relatorio.recursosHttpErro.filter((r) => r.url !== '/favicon.ico');
    const pageErrors = relatorio.errosConsole.filter((e) => e.tipo === 'pageerror');
    relatorio.verificacoes.zeroErrosJsEApiConsole = {
      passou: errosReaisHttp.length === 0 && pageErrors.length === 0,
      detalhe: `pageerror=${pageErrors.length}, httpErroNaoFavicon=${errosReaisHttp.length}, favicon404=${relatorio.recursosHttpErro.filter((r) => r.url === '/favicon.ico').length}`,
    };
    expect(pageErrors).toEqual([]);
    expect(errosReaisHttp).toEqual([]);
  });
});
