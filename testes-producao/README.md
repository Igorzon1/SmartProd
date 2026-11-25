# Testes em produção (AWS) — SmartProd

Scripts reutilizáveis para validar o SmartProd no ambiente real da AWS (Lambda Java 21 + Amazon Aurora PostgreSQL Serverless v2 + navegador real com Playwright em tablet `1180×820`).

> **Regra de segurança:** nunca grave URLs de banco, senhas, `JWT_SECRET` ou tokens neste diretório. Toda configuração entra por variáveis de ambiente e os arquivos temporários em `resultados/` são ignorados pelo `.gitignore`.

## Pré-requisitos

- **Node.js 20+**
- **Google Chrome** instalado (ou ajuste `PW_CHANNEL` / instale os navegadores com `npx playwright install`)
- Ambiente isolado de teste (ex.: clone *copy-on-write* do Aurora + Lambda `A3-teste`) com duas contas de teste (`GERENTE` e `FUNCIONARIO`).

```bash
cd testes-producao
PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 npm install
```

## Variáveis de ambiente

```powershell
$env:SMARTPROD_URL = "https://<sua-lambda-teste>.lambda-url.us-east-2.on.aws"
$env:SMARTPROD_GERENTE_EMAIL = "teste.gerente@exemplo.com"
$env:SMARTPROD_GERENTE_SENHA = "<senha-gerente-teste>"
$env:SMARTPROD_FUNC_EMAIL = "teste.func@exemplo.com"
$env:SMARTPROD_FUNC_SENHA = "<senha-funcionario-teste>"
```

## Execução

1. **Testes funcionais, regras de banco real e segurança (Seção 2):**

```bash
npm run funcional
```

2. **Semeadura de 500 OPs e desempenho quente (Seção 1 — RNF-D001):**

```bash
npm run desempenho
# Modos opcionais:
# node desempenho.mjs --modo=semeadura
# node desempenho.mjs --modo=quente
# node desempenho.mjs --modo=frio
```

3. **Frontend no navegador real em tablet `1180×820` (Seção 3 — RNF-C002, RNF-U001, RNF-U002):**

```bash
npm run e2e
# Para medir carga fria do Kanban após cold start:
# SMARTPROD_MODO_FRIO=1 npm run e2e
```

## Relatório completo

Os resultados obtidos em execução real na AWS (`us-east-2`) estão documentados em [`docs/testes-em-producao.md`](../docs/testes-em-producao.md).
