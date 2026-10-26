# Dupla 3 — Esteira de produção

**Responsáveis:** Backend: ____________ · Frontend: ____________

## Objetivo

Entregar o coração do sistema: **abrir Ordens de Produção**, acompanhá-las no **Kanban**, guiar o funcionário no **passo a passo com temporizadores** e **registrar perdas** na esteira.

## Requisitos

- **RF008** Abrir OPs definindo produto, quantidade a fabricar, horário de entrega e responsável.
- **RF009** Exibir as OPs em painel Kanban por colunas de status.
- **RF010** Guiar o funcionário etapa por etapa, com quantidades ajustadas ao lote e temporizadores.
- **RF011** Registrar perdas e refugos na esteira, informando etapa, quantidade e motivo.
- **RNF-D001** Kanban em ≤ 1,5 s. **RNF-D002** Índices nas colunas críticas. **RNF-U001** Botões ≥ 48 px, alto contraste. **RNF-U002** Máximo de 3 cliques por ação. **RNF-C002** Progresso da etapa salvo no navegador (`localStorage`).

## Antes de começar

Leia [CLAUDE.md](../../CLAUDE.md), [docs/09](../09-divisao-de-tarefas.md) (donos e contratos), [docs/07 §3](../07-dominio.md) (ciclo de vida da OP), [docs/02 §2.3](../02-requisitos.md) e [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Seus arquivos

- **Backend:** `producao/` e `perda/` em `backend/src/main/java/br/com/smartprod/`, os testes correspondentes e migrações novas.
- **Frontend:** `pages/ordens-producao/`, `pages/kanban/`, `pages/execucao/`.

**Não mexa** em `usuario/`, `auth/`, `insumo/`, `config/`, `receita/`, `dashboard/`, `js/core/`, `css/base.css` nem em `pages/` das outras duplas.

## O que você entrega aos outros

- **Migração e entidades de OP e de perda cedo** — a dupla 2 lê essas tabelas no dashboard.
- `GET /api/ordens-producao` e transições de status.

## O que você consome

- Login e `@PreAuthorize` (dupla 1).
- Fichas técnicas e cálculo da dupla 2 (`GET /api/fichas-tecnicas/{id}` e `/calculo`). Sua migração de OP tem chave estrangeira para a ficha: espere o PR de fichas da dupla 2 ser mergeado.

## Atenção

- A OP só segue `PENDENTE → EM_ANDAMENTO → CONCLUIDA`; transição inválida devolve 409. Perda só em OP `EM_ANDAMENTO`.
- Nome da coluna inicial do Kanban ("Pendente" ou "A Fazer") e etapas como colunas estão em aberto (Q5). Comece com as 3 colunas de status.
- "Quantidade produzida" ao concluir e formato de quantidade/unidade da perda estão em aberto (Q12 e Q13); confirme com o Igor.
- Interface touch: botões ≥ 48 px, no máximo 3 cliques por ação, progresso salvo em `localStorage` para sobreviver a queda de conexão.
- O Funcionário **nunca** vê custos ou valores em R$.

## Tarefas

| Código | Tarefa | Requisitos | Prioridade | Depende de |
|---|---|---|---|---|
| D3-BE-01 | Ordem de Produção: modelo, migração e abertura | RF008, RNF-D002 | Alta | D1-BE-02, D2-BE-01 |
| D3-BE-02 | Kanban e mudança de status | RF009, RNF-D001 | Alta | D3-BE-01 |
| D3-BE-03 | Dados do passo a passo | RF010 | Média | D3-BE-01, D2-BE-03 |
| D3-BE-04 | Registro de perdas e refugos | RF011, RNF-D002 | Alta | D3-BE-01 |
| D3-FE-01 | Tela de abrir OP | RF008 | Alta | D3-BE-01 |
| D3-FE-02 | Kanban de OPs | RF009, RNF-D001, RNF-U001 | Alta | D3-BE-02 |
| D3-FE-03 | Passo a passo com temporizadores | RF010, RNF-C002, RNF-U001, RNF-U002 | Média | D3-BE-03 |
| D3-FE-04 | Registro de perda na esteira | RF011, RNF-U002 | Alta | D3-BE-04, D3-FE-03 |

### D3-BE-01 — Ordem de Produção: modelo, migração e abertura

- **Parte:** backend · **Requisitos:** RF008, RNF-D002 · **Prioridade:** Alta
- **Depende de:** D1-BE-02, D2-BE-01
- **Arquivos:** `backend/src/main/java/br/com/smartprod/producao/`, `backend/src/main/resources/db/migration/`
- **O que fazer:** Entidade `OrdemProducao` (fichaTecnicaId, quantidadeUnidades, horarioEntrega, responsável, status PENDENTE|EM_ANDAMENTO|CONCLUIDA, criadoPor, iniciadaEm, concluidaEm, quantidadeProduzida), migração com índices (status, horarioEntrega) e `POST /api/ordens-producao` (só Gerente). A OP tem chave estrangeira para a ficha técnica: espere o PR de fichas da dupla 2. **Publique a migração cedo**: a dupla 2 lê essa tabela no dashboard.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D3-BE-02 — Kanban e mudança de status

- **Parte:** backend · **Requisitos:** RF009, RNF-D001 · **Prioridade:** Alta
- **Depende de:** D3-BE-01
- **Arquivos:** `backend/src/main/java/br/com/smartprod/producao/`
- **O que fazer:** `GET /api/ordens-producao` com filtros por status e data (resposta em ≤ 1,5 s), `PATCH /{id}/iniciar` e `PATCH /{id}/concluir` (aceita `quantidadeProduzida`). Transição inválida devolve 409. Funcionário inicia e conclui; Gerente vê tudo. Testes unitários das transições.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D3-BE-03 — Dados do passo a passo

- **Parte:** backend · **Requisitos:** RF010 · **Prioridade:** Média
- **Depende de:** D3-BE-01, D2-BE-03
- **Arquivos:** `backend/src/main/java/br/com/smartprod/producao/`
- **O que fazer:** `GET /api/ordens-producao/{id}/execucao`: devolve os passos da ficha com as quantidades ajustadas ao lote (usa o cálculo da dupla 2) e as durações para os temporizadores. Sem custos. Testes de integração.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D3-BE-04 — Registro de perdas e refugos

- **Parte:** backend · **Requisitos:** RF011, RNF-D002 · **Prioridade:** Alta
- **Depende de:** D3-BE-01
- **Arquivos:** `backend/src/main/java/br/com/smartprod/perda/`, `backend/src/main/resources/db/migration/`
- **O que fazer:** Entidade `RegistroPerda` (ordemId, etapa, quantidade e unidade, motivo, registradoPor, registradoEm), migração com índices, `POST /api/ordens-producao/{id}/perdas` (Gerente e Funcionário; só em OP EM_ANDAMENTO) e consulta por OP. Funcionário não vê valores em R$. **Publique a migração cedo** para a dupla 2. O formato da quantidade/unidade e o custo dependem da questão Q12: alinhe com o Igor.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D3-FE-01 — Tela de abrir OP

- **Parte:** frontend · **Requisitos:** RF008 · **Prioridade:** Alta
- **Depende de:** D3-BE-01
- **Arquivos:** `frontend/pages/ordens-producao/`
- **O que fazer:** `pages/ordens-producao` (Gerente): escolher a ficha, informar a quantidade, o horário de entrega e o responsável, e listar as OPs abertas. Use dados falsos até o backend estar pronto.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.

### D3-FE-02 — Kanban de OPs

- **Parte:** frontend · **Requisitos:** RF009, RNF-D001, RNF-U001 · **Prioridade:** Alta
- **Depende de:** D3-BE-02
- **Arquivos:** `frontend/pages/kanban/`
- **O que fazer:** `pages/kanban`: colunas Pendente, Em andamento e Concluída (nome da primeira coluna: questão Q5), cartões com produto, quantidade, horário e responsável, e botões para iniciar e concluir. Atualização automática a cada ~15 s. Recarga em ≤ 1,5 s e uso por toque.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.

### D3-FE-03 — Passo a passo com temporizadores

- **Parte:** frontend · **Requisitos:** RF010, RNF-C002, RNF-U001, RNF-U002 · **Prioridade:** Média
- **Depende de:** D3-BE-03
- **Arquivos:** `frontend/pages/execucao/`
- **O que fazer:** `pages/execucao`: mostra os ingredientes com as quantidades ajustadas, avança etapa por etapa e tem temporizador com alerta ao terminar. O progresso fica no `localStorage` para retomar após queda de conexão. No máximo 3 cliques por ação e botões ≥ 48 px.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.

### D3-FE-04 — Registro de perda na esteira

- **Parte:** frontend · **Requisitos:** RF011, RNF-U002 · **Prioridade:** Alta
- **Depende de:** D3-BE-04, D3-FE-03
- **Arquivos:** `frontend/pages/execucao/`
- **O que fazer:** Botão "Registrar perda" na tela de execução: etapa, quantidade e motivo, em no máximo 3 cliques, sem mostrar custos ao Funcionário.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.


## Como testar

```bash
cd backend && ./mvnw verify
```

Frontend: `npx serve -l 5500 frontend` e abra http://localhost:5500.

## Prompt para sua IA

> Leia CLAUDE.md, docs/09-divisao-de-tarefas.md, docs/07-dominio.md e docs/tarefas/dupla-3-esteira-de-producao.md. Sou da dupla 3 (parte: backend/frontend). Trabalhe só nos arquivos da minha dupla, escreva testes e rode ./mvnw verify antes de terminar.
