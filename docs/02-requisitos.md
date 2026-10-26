# 02 — Requisitos do Projeto

> Fonte: *Documento de Requisitos do Projeto* v1.0 (17/10/2026). Itens marcados **ALTERADO** divergem do PDF original por decisão posterior (ver [ADR-0001](decisoes/0001-backend-java-spring-boot.md)).

## 1. Elicitação e stakeholders

**Técnicas usadas:** pesquisa de mercado e análise online (fóruns de panificação, grupos de gestão de pequenas empresas, relatórios do SEBRAE); entrevistas e consultas informais com profissionais e gestores da área alimentícia; brainstorming com a equipe (resultou no fluxo Kanban e na Porcentagem do Padeiro).

| Stakeholder | Descrição | Necessidades principais |
|---|---|---|
| Gerente / Proprietário | Gerencia o negócio e os custos da padaria | Controlar custos, reduzir desperdício, ver indicadores de rendimento, padronizar o cardápio |
| Padeiro / Confeiteiro | Responsável pela produção e preparo | Ver o passo a passo, receber a quantidade exata de ingredientes já calculada, usar temporizadores |
| Ajudante de Cozinha | Apoio na montagem e separação de insumos | Interface simples e rápida (touchscreen) para seguir OPs e registrar refugos/perdas |

> No sistema existem **dois perfis de acesso**: `GERENTE` e `FUNCIONARIO` (Padeiro e Ajudante usam o perfil Funcionário). Ver [questão Q1](08-questoes-em-aberto.md).

## 2. Requisitos funcionais (RF)

Prioridade: Alta / Média. Status de todos: **A implementar** (o RF011 estava sem status no PDF).

### 2.1 Autenticação e usuários — F1

| ID | Descrição | Prioridade |
|---|---|---|
| RF001 | Autenticar usuários por e-mail e senha, direcionando o acesso conforme o perfil (Gerente ou Funcionário) | Alta |
| RF002 | O gerente cadastra, edita, inativa e consulta contas de usuários/colaboradores | Alta |
| RF003 | Disponibilizar ao funcionário uma interface simplificada, com botões amplos, para telas touchscreen no chão de fábrica | Alta |

### 2.2 Insumos e fichas técnicas — F2 e F3

| ID | Descrição | Prioridade |
|---|---|---|
| RF004 | Cadastrar insumos com nome, unidade de medida (g, kg, ml, L, un) e custo unitário | Alta |
| RF005 | Cadastrar fichas técnicas (receitas) com insumos, quantidades base, modo de preparo passo a passo e rendimento padrão | Alta |
| RF006 | Calcular e exibir a proporção dos ingredientes pela Porcentagem do Padeiro (peso da farinha = 100%) | Média |
| RF007 | Recalcular os ingredientes conforme a quantidade desejada ou a matéria-prima disponível em estoque | Média |

### 2.3 Esteira de produção — F4 e F5

| ID | Descrição | Prioridade |
|---|---|---|
| RF008 | Abrir Ordens de Produção (OPs) definindo produto, quantidade a fabricar, horário de entrega e responsável | Alta |
| RF009 | Exibir as OPs em painel Kanban estruturado por colunas de status | Alta |
| RF010 | Guiar o funcionário etapa por etapa da receita, exibindo quantidades ajustadas ao lote e temporizadores | Média |
| RF011 | Registrar perdas e refugos operacionais na esteira, informando etapa, quantidade perdida e motivo | Alta |

### 2.4 Dashboard operacional — F6

| ID | Descrição | Prioridade |
|---|---|---|
| RF012 | Indicador do rendimento diário: volume fabricado × previsto | Média |
| RF013 | Total acumulado de desperdício no período em peso (kg) e custo (R$) | Alta |
| RF014 | Ranking dos 3 produtos com maior índice de desperdício no período | Média |
| RF015 | Status consolidado das OPs (pendentes, em andamento, concluídas) em tempo real | Alta |

## 3. Requisitos não-funcionais (RNF)

| ID | Categoria | Descrição | Métrica / implementação |
|---|---|---|---|
| RNF-D001 | Desempenho | Cálculo proporcional e recarga do Kanban em no máximo 1,5 s | Tempo de resposta ≤ 1,5 s |
| RNF-D002 | Desempenho | Índices nas colunas de consulta frequente (OPs, receitas e insumos) | Índices nas colunas críticas |
| RNF-S001 | Segurança | Senhas criptografadas com bcrypt ou similar | BCrypt, ≥ 10 rounds (projeto usa 12) |
| RNF-S002 | Segurança | Dados financeiros (custos de insumos, relatórios de desperdício) restritos ao Gerente | RBAC. **ALTERADO:** Spring Security com `@PreAuthorize` (antes: "middleware") |
| RNF-U001 | Usabilidade | Interface operacional com alto contraste, fontes grandes e botões para touchscreen | Botão com área de toque ≥ 48 px |
| RNF-U002 | Usabilidade | No máximo 3 cliques por ação operacional (etapas ou OP) | ≤ 3 cliques |
| RNF-C001 | Confiabilidade | Disponibilidade mínima de 99,5% nos horários de operação | Downtime ≤ ~3,6 h/mês |
| RNF-C002 | Confiabilidade | Progresso da etapa atual da receita salvo localmente no navegador | `localStorage` / IndexedDB |
| RNF-Comp001 | Compatibilidade | Navegadores modernos | Chrome, Firefox, Safari, Edge (versões atuais) |
| RNF-Comp002 | Arquitetura | **ALTERADO.** Original: "JavaScript unificado no front e no back (Node.js)". **Atual:** Java 21 + Spring Boot no backend; HTML/CSS/JavaScript no frontend | Ver ADR-0001 |

> RNF-D002 no PDF cita "OIDs"; interpretado como **OPs** (Ordens de Produção).

## 4. Restrições técnicas

| Item | Original (PDF) | Atual |
|---|---|---|
| Linguagem | JavaScript (front e back) | **ALTERADO:** Java 21 (back) + JavaScript (front) |
| Frontend | HTML5, CSS3, JavaScript | Mantido |
| Backend | Node.js com Express (ou NestJS) | **ALTERADO:** Spring Boot 3.5 |
| Banco de dados | PostgreSQL ou MySQL | **PostgreSQL** (H2 em dev/testes) |
| Hospedagem | Render / Vercel / Heroku | Render / Vercel (backend Java precisa de Dockerfile no Render) |
| Prazo | Conforme cronograma acadêmico | [05-regras-a3.md](05-regras-a3.md) |
| Orçamento | Ferramentas gratuitas ou *free tier* | Mantido |

## 5. Premissas e dependências

**Premissas:** a cozinha tem tablet ou computador com internet; os colaboradores recebem treinamento básico sobre o Kanban; o gerente mantém o custo dos insumos atualizado.

**Dependências externas:** hospedagem de backend e frontend (Render, Vercel); banco PostgreSQL em nuvem (Supabase, Neon, Render PostgreSQL).

## 6. Critérios de aceitação gerais

O projeto estará concluído quando:

1. Os **15 requisitos funcionais** estiverem implementados e testados.
2. Os **10 requisitos não-funcionais** forem respeitados (principalmente tempo de resposta e restrição de acesso financeiro).
3. A interface operacional funcionar de forma responsiva em tablets na cozinha.
4. O código estiver versionado no Git com **commits descritivos**.

## 7. Glossário

| Termo | Definição |
|---|---|
| Ordem de Produção (OP) | Solicitação formal de fabricação de um lote de um produto para um horário específico |
| Porcentagem do Padeiro | Convenção da panificação: farinha de trigo = 100% e os demais ingredientes são percentuais em relação a ela |
| Ficha Técnica | Cadastro estruturado da receita: lista proporcional de ingredientes, custo e modo de preparo |
| Kanban | Painel visual em colunas de status para organizar o fluxo e o progresso do trabalho |
| Refugo / Perda Operacional | Desperdício de ingrediente ou produto por erro no preparo, fornada queimada ou avaria |

## 8. Histórico de versões

| Versão | Data | Autor | Alterações |
|---|---|---|---|
| 1.0 | 17/10/2026 | Equipe do Projeto | Versão inicial do Documento de Requisitos (PDF) |
| 1.1 | 26/10/2026 | Equipe do Projeto | Versão em Markdown; backend alterado de Node.js para Java/Spring Boot (ADR-0001) |
