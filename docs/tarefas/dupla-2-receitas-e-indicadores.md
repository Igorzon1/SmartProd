# Dupla 2 — Receitas e indicadores

**Responsáveis:** Backend: ____________ · Frontend: ____________

## Objetivo

Entregar as **fichas técnicas** com a **Porcentagem do Padeiro**, a **calculadora** proporcional (por demanda e por estoque) e, no final, o **dashboard do gerente**.

## Requisitos

- **RF005** Cadastrar fichas técnicas (receitas) com insumos, quantidades base, modo de preparo passo a passo e rendimento padrão.
- **RF006** Calcular e exibir a proporção pela Porcentagem do Padeiro (farinha = 100%).
- **RF007** Recalcular os ingredientes pela quantidade desejada ou pela matéria-prima disponível em estoque.
- **RF012** Rendimento diário: produzido × previsto. **RF013** Desperdício acumulado em kg e R$. **RF014** Ranking dos 3 produtos mais desperdiçados. **RF015** Status consolidado das OPs.
- **RNF-D001** Cálculo e Kanban em ≤ 1,5 s. **RNF-D002** Índices nas colunas críticas. **RNF-S002** Dashboard e custos só para Gerente.

## Antes de começar

Leia [CLAUDE.md](../../CLAUDE.md), [docs/09](../09-divisao-de-tarefas.md) (donos e contratos), [docs/07](../07-dominio.md) (fórmulas), [docs/02 §2.2 e §2.4](../02-requisitos.md) e [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Seus arquivos

- **Backend:** `receita/` e `dashboard/` em `backend/src/main/java/br/com/smartprod/`, os testes correspondentes e migrações novas.
- **Frontend:** `pages/fichas-tecnicas/`, `pages/calculadora/`, `pages/dashboard/`.

**Não mexa** em `usuario/`, `auth/`, `insumo/`, `config/`, `producao/`, `perda/`, `js/core/`, `css/base.css` nem em `pages/` das outras duplas.

## O que você entrega aos outros

- **Primeiro PR:** migração e entidades de ficha técnica + `GET /api/fichas-tecnicas` e `/{id}` — a dupla 3 espera por isso.
- `GET /api/fichas-tecnicas/{id}/calculo` (a dupla 3 usa no passo a passo).

## O que você consome

- Insumos da dupla 1 (`GET /api/insumos`) e login/`@PreAuthorize`.
- Tabelas `ordens_producao` e `registros_perda` da dupla 3 (só leitura, no dashboard).

## Atenção

- Fórmulas em [docs/07 §2](../07-dominio.md). Use `BigDecimal` e defina a escala de arredondamento nos testes.
- Estoque (RF007): não existe cadastro de estoque; a quantidade disponível vem **na requisição** (questão Q2).
- Conversão de unidades (g/kg, ml/L) e custo da perda dependem das questões Q7 e Q12; confirme com o Igor antes de implementar o dashboard.
- Frontend do dashboard: use dados falsos em `pages/dashboard/mock/` até a dupla 3 terminar.

## Tarefas

| Código | Tarefa | Requisitos | Prioridade | Depende de |
|---|---|---|---|---|
| D2-BE-01 | Fichas técnicas: modelo, migração e CRUD | RF005, RNF-D002 | Alta | D1-BE-02, D1-BE-04 |
| D2-BE-02 | Porcentagem do Padeiro | RF006 | Média | D2-BE-01 |
| D2-BE-03 | Calculadora por demanda e por estoque | RF007, RNF-D001 | Média | D2-BE-02 |
| D2-BE-04 | Indicador de rendimento diário | RF012 | Média | D3-BE-01, D3-BE-02 |
| D2-BE-05 | Desperdício acumulado (kg e R$) | RF013 | Alta | D3-BE-04 |
| D2-BE-06 | Ranking dos 3 produtos mais desperdiçados | RF014 | Média | D3-BE-04 |
| D2-BE-07 | Status consolidado das OPs | RF015 | Alta | D3-BE-01 |
| D2-FE-01 | Tela de fichas técnicas | RF005 | Alta | D2-BE-01 |
| D2-FE-02 | Porcentagem do Padeiro e calculadora | RF006, RF007 | Média | D2-BE-03 |
| D2-FE-03 | Dashboard do Gerente | RF012, RF013, RF014, RF015 | Alta | D2-BE-04, D2-BE-05, D2-BE-06, D2-BE-07 |

### D2-BE-01 — Fichas técnicas: modelo, migração e CRUD

- **Parte:** backend · **Requisitos:** RF005, RNF-D002 · **Prioridade:** Alta
- **Depende de:** D1-BE-02, D1-BE-04
- **Arquivos:** `backend/src/main/java/br/com/smartprod/receita/`, `backend/src/main/resources/db/migration/`
- **O que fazer:** Entidades `FichaTecnica`, `FichaIngrediente` (insumo, quantidadeBase, ehFarinha) e `PassoPreparo` (ordem, etapa, descricao, duracaoSegundos opcional), migração Flyway com índices e endpoints `/api/fichas-tecnicas` (Gerente escreve; Funcionário lê sem custos). **Abra primeiro um PR só com a migração, as entidades e os `GET`**: a dupla 3 espera por isso.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D2-BE-02 — Porcentagem do Padeiro

- **Parte:** backend · **Requisitos:** RF006 · **Prioridade:** Média
- **Depende de:** D2-BE-01
- **Arquivos:** `backend/src/main/java/br/com/smartprod/receita/`
- **O que fazer:** Serviço puro que calcula o percentual de cada ingrediente (farinha = 100%; havendo várias farinhas, juntas somam 100%) e inclui `percentualPadeiro` no `GET` da ficha. Erro claro se não houver farinha. Testes unitários com exemplos (1000 g de farinha e 700 g de água dão 70%).
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D2-BE-03 — Calculadora por demanda e por estoque

- **Parte:** backend · **Requisitos:** RF007, RNF-D001 · **Prioridade:** Média
- **Depende de:** D2-BE-02
- **Arquivos:** `backend/src/main/java/br/com/smartprod/receita/`
- **O que fazer:** `GET /api/fichas-tecnicas/{id}/calculo?unidades=N` (recalcula pela proporção) e `POST /api/fichas-tecnicas/{id}/calculo-por-estoque` (recebe o estoque por insumo e devolve as unidades possíveis e o ingrediente limitante), conforme o docs/09. Use `BigDecimal` e responda em ≤ 1,5 s. A dupla 3 usa o primeiro no passo a passo. Testes unitários de borda (estoque zero, insumo faltando).
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D2-BE-04 — Indicador de rendimento diário

- **Parte:** backend · **Requisitos:** RF012 · **Prioridade:** Média
- **Depende de:** D3-BE-01, D3-BE-02
- **Arquivos:** `backend/src/main/java/br/com/smartprod/dashboard/`
- **O que fazer:** `GET /api/dashboard/rendimento?data=`: produzido × previsto do dia, em %. Previsto = soma das OPs do dia; "produzido" depende da questão Q13. Só Gerente. Lê as tabelas da dupla 3 somente para leitura, com consultas no pacote `dashboard/`. Testes com OPs inseridas no próprio teste.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D2-BE-05 — Desperdício acumulado (kg e R$)

- **Parte:** backend · **Requisitos:** RF013 · **Prioridade:** Alta
- **Depende de:** D3-BE-04
- **Arquivos:** `backend/src/main/java/br/com/smartprod/dashboard/`
- **O que fazer:** `GET /api/dashboard/desperdicio?de=&ate=`: total perdido no período em kg e em R$. Só Gerente (RNF-S002). A conversão de unidades e o custo da perda dependem das questões Q7 e Q12: confirme com o Igor antes.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D2-BE-06 — Ranking dos 3 produtos mais desperdiçados

- **Parte:** backend · **Requisitos:** RF014 · **Prioridade:** Média
- **Depende de:** D3-BE-04
- **Arquivos:** `backend/src/main/java/br/com/smartprod/dashboard/`
- **O que fazer:** `GET /api/dashboard/ranking-desperdicio?de=&ate=`: os 3 produtos com maior desperdício no período (kg e R$). Só Gerente. Testes com empates e com menos de 3 produtos.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D2-BE-07 — Status consolidado das OPs

- **Parte:** backend · **Requisitos:** RF015 · **Prioridade:** Alta
- **Depende de:** D3-BE-01
- **Arquivos:** `backend/src/main/java/br/com/smartprod/dashboard/`
- **O que fazer:** `GET /api/dashboard/status-ops`: contagem de OPs pendentes, em andamento e concluídas. Só Gerente. Testes de integração com MockMvc.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D2-FE-01 — Tela de fichas técnicas

- **Parte:** frontend · **Requisitos:** RF005 · **Prioridade:** Alta
- **Depende de:** D2-BE-01
- **Arquivos:** `frontend/pages/fichas-tecnicas/`
- **O que fazer:** `pages/fichas-tecnicas` (Gerente): listar, criar e editar fichas com tabela de ingredientes (insumo, quantidade base, é farinha) e de passos (ordem, etapa, descrição, duração). Use dados falsos até o backend estar pronto.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.

### D2-FE-02 — Porcentagem do Padeiro e calculadora

- **Parte:** frontend · **Requisitos:** RF006, RF007 · **Prioridade:** Média
- **Depende de:** D2-BE-03
- **Arquivos:** `frontend/pages/calculadora/`
- **O que fazer:** `pages/calculadora`: escolher a ficha, informar as unidades desejadas OU o estoque disponível e mostrar as quantidades recalculadas e o percentual do padeiro. Gerente e Funcionário acessam; sem custos.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.

### D2-FE-03 — Dashboard do Gerente

- **Parte:** frontend · **Requisitos:** RF012, RF013, RF014, RF015 · **Prioridade:** Alta
- **Depende de:** D2-BE-04, D2-BE-05, D2-BE-06, D2-BE-07
- **Arquivos:** `frontend/pages/dashboard/`
- **O que fazer:** `pages/dashboard` (só Gerente): cartões de rendimento diário, desperdício (kg e R$), ranking Top 3 e status das OPs. Gráficos simples; combinem antes se for usar biblioteca externa. Use dados falsos (`pages/dashboard/mock/`) até a dupla 3 terminar.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.


## Como testar

```bash
cd backend && ./mvnw verify
```

Frontend: `npx serve -l 5500 frontend` e abra http://localhost:5500.

## Prompt para sua IA

> Leia CLAUDE.md, docs/09-divisao-de-tarefas.md, docs/07-dominio.md e docs/tarefas/dupla-2-receitas-e-indicadores.md. Sou da dupla 2 (parte: backend/frontend). Trabalhe só nos arquivos da minha dupla, escreva testes e rode ./mvnw verify antes de terminar.
