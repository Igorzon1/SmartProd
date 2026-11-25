# Testes e Medições em Ambiente Real AWS — SmartProd

Relatório de validação ponta a ponta do **SmartProd** no ambiente real da AWS (Java 21 + Spring Boot 3.5 em AWS Lambda + Amazon Aurora PostgreSQL 17.9 Serverless v2 + navegador real Google Chrome via Playwright em tablet `1180×820`), cobrindo desempenho (RNF-D001), regras funcionais e de banco real, segurança, usabilidade (RNF-U001 / RNF-U002) e confiabilidade local (RNF-C002).

> **Segurança e isolamento:** todos os testes foram executados em um ambiente isolado criado exclusivamente para esta bateria — **clone *copy-on-write* do Aurora (`database-1-teste`)** e **segunda função Lambda (`A3-teste`)** na região `us-east-2` — sem alterar nenhum dado da produção (`database-1` e `A3`). Nenhum segredo (`JWT_SECRET`, senhas, tokens ou URL de banco) consta neste documento ou nos scripts. Ao término, ambos os recursos temporários foram apagados.

---

## 1. Ambiente e Metodologia

| Item | Configuração medida |
|---|---|
| **Data da execução** | 25/11/2026 (horário de Brasília, `America/Sao_Paulo`) |
| **Região AWS** | `us-east-2` (Ohio, EUA) |
| **Cliente de medição** | Máquina Windows 11 no Brasil (Node.js v24.11.0 com `fetch` + `perf_hooks`; Playwright `@playwright/test` + Google Chrome headless em viewport tablet `1180×820`, `locale: pt-BR`, `timezoneId: America/Sao_Paulo`) |
| **AWS Lambda (espelho da `A3`)** | `A3-teste` · Runtime `java21` · Arquitetura `x86_64` · Memória `1.769 MB` · Timeout `60 s` · Pacote `37,85 MB` (idêntico à `A3`) · `JAVA_TOOL_OPTIONS=-XX:+TieredCompilation -XX:TieredStopAtLevel=1` · Function URL (`AuthType=NONE`) |
| **Banco de dados (clone da `database-1`)** | `database-1-teste` · Amazon Aurora PostgreSQL `17.9` (`aarch64`) · Serverless v2 (`MinCapacity: 0.0 ACU`, `MaxCapacity: 4.0 ACU`, `SecondsUntilAutoPause: 300 s`) · Autenticação IAM SigV4 + TLS (`sslmode=require`) · Internet Access Gateway |
| **Massa de dados (Seção 1)** | 500 OPs semeadas via API (`198` pendentes, `124` em andamento, `173` concluídas + 5 preexistentes da seção funcional) com datas variadas; total visível no Kanban padrão: `500–503` OPs (~170 KB de JSON) |
| **Contas de teste** | Contas dedicadas (`GERENTE` e `FUNCIONARIO`) criadas apenas no clone, sem uso da conta de exemplo do perfil `dev` |

---

## 2. Seção 1 — Desempenho Real na AWS (RNF-D001 ≤ 1.500 ms)

### 2.1 Cenário Quente (cliente Brasil → AWS `us-east-2`, 2 aquecimentos + 30 medições por rota)

Todas as 12 medições quentes atenderam **com folga** ao critério **RNF-D001 (p95 ≤ 1.500 ms)** mesmo somando a latência de rede Brasil ↔ Ohio (~150 ms por chamada HTTPS):

| Rota | Payload / Itens | Mín (ms) | p50 (ms) | p95 (ms) | p99 (ms) | Máx (ms) | RNF-D001 (p95 ≤ 1,5 s) |
|---|---:|---:|---:|---:|---:|---:|:---:|
| `GET /api/ordens-producao` (Kanban padrão) | 170 KB / 500 OPs | 200,1 | 212,3 | **610,0** | 612,1 | 612,1 | **Passou** |
| `GET /api/ordens-producao?status=PENDENTE` | 62 KB / 198 OPs | 176,5 | 182,5 | **187,3** | 187,7 | 187,7 | **Passou** |
| `GET /api/ordens-producao?data=2026-11-25` | 102 KB / 289 OPs | 191,2 | 206,0 | **338,1** | 341,4 | 341,4 | **Passou** |
| `GET /api/ordens-producao?status=PENDENTE&data=2026-11-25` | 15 KB / 49 OPs | 168,4 | 173,1 | **177,4** | 180,6 | 180,6 | **Passou** |
| `GET /api/fichas-tecnicas/1/calculo?unidades=150` | 167 B | 160,7 | 164,6 | **170,8** | 172,8 | 172,8 | **Passou** |
| `POST /api/fichas-tecnicas/1/calculo-por-estoque` | 226 B | 160,9 | 164,3 | **169,3** | 208,1 | 208,1 | **Passou** |
| `GET /api/dashboard/rendimento` | 64 B | 158,4 | 161,7 | **171,7** | 269,5 | 269,5 | **Passou** |
| `GET /api/dashboard/desperdicio` | 36 B | 160,2 | 164,5 | **171,3** | 172,5 | 172,5 | **Passou** |
| `GET /api/dashboard/ranking-desperdicio` | 181 B | 160,9 | 165,7 | **173,6** | 175,9 | 175,9 | **Passou** |
| `GET /api/dashboard/status-ops` | 52 B | 155,6 | 161,8 | **173,5** | 174,3 | 174,3 | **Passou** |
| `GET /api/dashboard/*` (4 chamadas paralelas da tela) | 333 B | 167,2 | 173,3 | **245,0** | 457,2 | 457,2 | **Passou** |
| `POST /api/auth/login` (BCrypt custo 12) | 318 B | 533,4 | 541,7 | **549,1** | 550,0 | 550,0 | **Passou** |

**Separação servidor × rede (CloudWatch Logs — linhas `REPORT` das execuções quentes, `n = 1.561` invocações):**
- **Tempo de servidor (`Duration` na Lambda + consulta ao Aurora):** mín `4,35 ms` · **p50 `19,09 ms`** · **p95 `50,55 ms`** · p99 `394,19 ms` (inclui `POST /api/auth/login` com BCrypt custo 12, que consome ~380 ms de CPU) · máx `1.188,38 ms`.
- **Memória usada (`Max Memory Used`):** `417 MB` a `442 MB` de `1.769 MB` alocados (~25% de utilização, ampla folga).
- **Latência de rede Brasil ↔ `us-east-2`:** ~150–160 ms por requisição (ou ~350–450 ms adicionais quando o payload ultrapassa ~100–170 KB sem compressão/paginação, como no Kanban com 500 OPs completas).

### 2.2 Cenário Frio da Lambda (5× cold starts forçados, Aurora quente, `GET /api/ordens-producao` com 503 OPs)

| Execução | HTTP | Cliente (ms) | CloudWatch `Init Duration` (ms) | CloudWatch `Duration` (ms) | `Billed Duration` (ms) | `Max Memory Used` |
|---:|---:|---:|---:|---:|---:|---:|
| 1 | 200 | 12.082,8 | 248,85 | 10.479,20 | 10.729 | 423 MB |
| 2 | 200 | 11.974,5 | 263,83 | 10.668,39 | 10.933 | 424 MB |
| 3 | 200 | 12.455,2 | 267,02 | 11.149,97 | 11.417 | 422 MB |
| 4 | 200 | 12.007,3 | 248,70 | 10.695,08 | 10.944 | 423 MB |
| 5 | 200 | 12.022,0 | 272,71 | 10.658,16 | 10.931 | 423 MB |
| **Resumo** | **5/5 OK** | **mín 11,97 s · p50 12,02 s · máx 12,46 s** | **p50 263,8 ms** | **p50 10,67 s** | **p50 10,93 s** | **422–424 MB** |

> *Observação arquitetural:* o `Init Duration` da fase `INIT` fica em ~260 ms porque `StreamLambdaHandler` faz inicialização *lazy* do contexto Spring Boot dentro da primeira chamada `handleRequest` (evitando estourar o limite de 10 s da fase `INIT` da Lambda). Por isso, o tempo total de subida do Spring Boot + Hibernate + Flyway aparece em `Duration` (~10,7 s).

### 2.3 Cenário Frio do Aurora (3× após auto-pause confirmado em `0.0 ACU`, Lambda quente, `GET /api/ordens-producao` com 503 OPs)

Antes de cada disparo, confirmou-se via CloudWatch Metrics (`AWS/RDS ServerlessDatabaseCapacity`) que o cluster `database-1-teste` havia efetivamente pausado (`0.0 ACU`), mantendo o container Lambda quente via ping estático em `/index.html` (sem tocar no banco):

| Execução | Tempo ocioso do banco | ACU antes | HTTP | Cliente (ms) | CloudWatch `Duration` (ms) | `Max Memory Used` | Passou de 45 s? |
|---:|---:|---:|---:|---:|---:|---:|:---:|
| 1 *(+ teste IAM > 15 min)* | 15,84 min | 0,0 | 200 | 18.264,8 | 17.346,01 | 430 MB | **Não** |
| 2 | 9,13 min | 0,0 | 200 | 19.505,9 | 18.035,90 | 433 MB | **Não** |
| 3 | 9,90 min | 0,0 | 200 | 17.490,8 | 16.086,89 | 433 MB | **Não** |
| **Resumo** | **9,1–15,8 min** | **0,0 ACU** | **3/3 OK (0 erros 500)** | **mín 17,49 s · p50 18,26 s · máx 19,51 s** | **p50 17,35 s** | **430–433 MB** | **0/3 (> 25 s de folga)** |

### 2.4 Carga Completa do Kanban no Navegador (Playwright, tablet `1180×820`, 502–503 OPs)

| Cenário no navegador real | Amostras (ms) | Mediana (ms) | Observação |
|---|---:|---:|---|
| **Recarga quente (botão `Atualizar`)** | 443,8 · 463,0 · 466,9 | **463,0 ms** | Atende RNF-D001 (≤ 1.500 ms) mesmo renderizando 503 cartões no DOM |
| **Navegação completa quente (`page.goto`)** | 1.718,7 · 1.601,0 · 1.577,3 | **1.601,0 ms** | ~101 ms acima de 1.500 ms devido a múltiplos arquivos JS/CSS em cadeia pela rede Brasil↔Ohio + 503 cartões sem paginação |
| **Navegação completa fria (Lambda fria)** | 28.757,1 | **28.757,1 ms (28,8 s)** | Navegador requisita HTML, CSS e módulos ES em paralelo, disparando 3 containers frios da Lambda (`10,9 s`, `10,7 s` e `13,4 s`) |

### 2.5 Mitigações recomendadas para o cenário frio e primeira carga
1. **AWS Lambda SnapStart (Java 21):** reduz o cold start da JVM/Spring Boot de ~12 s para ~1–2 s sem custo adicional de hora ociosa.
2. **Amazon S3 + CloudFront para `frontend/`:** elimina invocações frias da Lambda para servir HTML/CSS/JS e reduz a navegação quente do Kanban para < 1.000 ms (evita o efeito de 3 containers frios em paralelo na carga fria do navegador, derrubando de 28,8 s para ~12 s ou ~2 s com SnapStart).
3. **Aurora Serverless v2 com `MinCapacity = 0.5 ACU`** (em horários de expediente da padaria): elimina o despertar de ~18 s do auto-pause (mantendo `0.0 ACU` apenas fora de produção/demonstração para economizar).
4. **Concorrência Provisionada (1 instância)** ou **região `sa-east-1` (São Paulo):** elimina o cold start da Lambda e reduz a latência de rede de ~150 ms para ~15–25 ms.

---

## 3. Seção 2 — Testes Funcionais, Banco Real e Segurança na AWS

### 3.1 Banco e Infraestrutura

| Item | Status | Evidência |
|---|:---:|---|
| Tabela `flyway_schema_history` no Aurora com todas as migrações `success = true` | **Passou** | 6/6 migrações idênticas ao repositório (`V1`, `V202611042200`, `V202611151500`, `V202611172000`, `V202611172001`, `V202611181900`), todas `success=true` (tempos de 9 ms a 72 ms) no PostgreSQL `17.9` |
| Autenticação IAM continua funcionando após > 15 min ocioso | **Passou** | Após **15,84 min** sem consultas ao banco no mesmo container Lambda quente (sem `Init Duration` no `REPORT`, `Duration: 17.346 ms`), `IamDataSourceConfig` renovou o token SigV4 automaticamente e `GET /api/ordens-producao` retornou `HTTP 200` (503 OPs) |
| Banco acordando de auto-pause responde sem erro 500 | **Passou** | 3/3 despertares a partir de `0.0 ACU` confirmados no CloudWatch responderam `HTTP 200` (`18,26 s`, `19,51 s` e `17,49 s`), zero `500` |
| Acentos UTF-8 corretos em HTML, CSS, JS e respostas JSON | **Passou** | `Content-Type` com `charset=UTF-8` em `/`, `/pages/login/index.html`, `/pages/kanban/index.html`, `/css/base.css`, `/js/core/api.js` (0 mojibake); JSON persistido e lido do Aurora preservou `"Pão francês"`, `"Fermento biológico"`, `"Água filtrada"` |

### 3.2 Fluxo Completo pela API

| Item | Status | Evidência |
|---|:---:|---|
| Login de gerente e funcionário devolve token JWT | **Passou** | `POST /api/auth/login` → gerente `HTTP 200` (`559,4 ms`, `GERENTE`); funcionário `HTTP 200` (`545,5 ms`, `FUNCIONARIO`) |
| Criar insumos e ficha técnica; validar Porcentagem do Padeiro e custo/kg | **Passou** | `POST /api/insumos` (4× `201`), `POST /api/fichas-tecnicas` (`201`): `percentualPadeiro = [100, 60, 2, 2]`; `/api/fichas-tecnicas/custos` (`200`): `custoReceita = 7.98`, `custoPorKg = 4.87` (`7,98 ÷ 1,64 kg = 4,8658...`) |
| Calculadora por demanda e por estoque (ingrediente limitante) | **Passou** | `GET /api/fichas-tecnicas/{id}/calculo?unidades=100` → `200` (`5.000 g` farinha); `POST /calculo-por-estoque` → `200`, `unidadesPossiveis = 40`, `ingredienteLimitante = Fermento biológico` |
| Ciclo completo OP: abrir → iniciar → perda → concluir | **Passou** | Abrir `201` (#35) → iniciar `200` (`EM_ANDAMENTO`) → `POST /perdas` `201` (`1,5 kg`, `18 un`) → concluir `200` (`CONCLUIDA`, `quantidadeProduzida = 45`) |
| Dashboard reflete OP concluída e perda em kg e R$ | **Passou** | `rendimento`: `previsto=100, produzido=90 (90%)`; `desperdicio`: `3 kg / R$ 14,60`; `ranking`: ficha teste com `1,5 kg` e `R$ 7,30` (`1,5 × 7,98 / 1,64 = 7,2988 → R$ 7,30`, cálculo exato sem arredondamento prematuro); `status-ops`: `concluidas=2` |
| Cancelar OP pendente (204) vs OP em andamento (409 pt-BR) | **Passou** | `DELETE` OP pendente → `HTTP 204`; `DELETE` OP em andamento → `HTTP 409`, `detail`: *"Só dá para cancelar uma OP pendente. Uma OP que já começou deve ser concluída com a quantidade que saiu."* |

### 3.3 Regras Dependentes do Banco Real (PostgreSQL 17.9)

| Item | Status | Evidência |
|---|:---:|---|
| **Concorrência real** ao concluir mesma OP (`@Version` optimistic lock) | **Passou** | Duas chamadas `PATCH /concluir` paralelas (`28` vs `29` un): `HTTP 200` + `HTTP 409` com `detail`: *"Outra pessoa alterou este registro ao mesmo tempo. Atualize a tela e tente de novo."*; valor persistido = `29` (do vencedor) |
| **Fuso horário `America/Sao_Paulo`** (entrega às 23:30 Brasília = 02:30Z do dia seguinte) | **Passou** | OP criada com `2026-11-27T23:30:00-03:00` (`2026-11-28T02:30:00Z`) apareceu em `data=2026-11-27` no Kanban (`temOP=true`) e no Dashboard (`previsto=33`), e **não** vazou para `2026-11-28` (`temOP=false`, `previsto=0`) |
| Cadastro concorrente de dois usuários com o mesmo e-mail | **Passou** | Duas chamadas `POST /api/usuarios` paralelas com mesmo e-mail: `HTTP 201` + `HTTP 409` (*"Já existe um usuário com este e-mail."*) |

### 3.4 Segurança no Ambiente Real

| Item | Status | Evidência |
|---|:---:|---|
| Sem token: `401` em todas as rotas `/api/**` exceto login | **Passou** | 11/11 rotas testadas (`insumos`, `fichas-tecnicas`, `custos`, `ordens-producao`, `usuarios`, 4× `dashboard`, `POST`s) retornaram `HTTP 401` |
| Funcionário recebe `403` nas rotas de gerente | **Passou** | 9/9 rotas restritas (`usuarios`, `POST insumos`, `fichas/custos`, `POST ordens-producao`, 4× `dashboard`) retornaram `HTTP 403` |
| Funcionário sem `custoUnitario` nem valores em R$ (RNF-S002) | **Passou** | 5/5 endpoints acessíveis ao funcionário (`GET /api/insumos`, `/insumos/{id}`, `/fichas-tecnicas`, `/fichas-tecnicas/{id}`, `/ordens-producao/{id}/execucao`) retornaram `HTTP 200` com **zero** campos financeiros no JSON |
| Usuário inativado tem token revogado na hora (`401`) | **Passou** | Antes de inativar: `HTTP 200`; `PATCH /api/usuarios/{id}/inativar`: `HTTP 200`; chamada imediata com o mesmo token: `HTTP 401` (*"Faça login para continuar."*) |
| Erros em RFC 7807 (português), sem stack trace | **Passou** | 4/4 cenários de erro (`404`, `400` tipo inválido, `400` validação) retornaram `application/problem+json` em português com `stackTrace=false` |
| *(Estado atual — pré-correções de segurança pendentes)* Cabeçalhos CSP e HSTS | **Pendente** | `X-Frame-Options: DENY` e `X-Content-Type-Options: nosniff` já presentes; **`Content-Security-Policy` e `Strict-Transport-Security` ausentes** (conforme esperado antes do endurecimento) |
| *(Estado atual — pré-correções de segurança pendentes)* Rate limiting no login (`429`) | **Pendente** | 6 tentativas inválidas seguidas retornaram `[401, 401, 401, 401, 401, 401]` (sem bloqueio `429` ainda) |
| *(Estado atual — pré-correções de segurança pendentes)* CORS recusando `http://localhost:5500` | **Pendente** | `OPTIONS /api/auth/login` com `Origin: http://localhost:5500` retornou `HTTP 200` com `Access-Control-Allow-Origin: http://localhost:5500` (ainda listado na variável `CORS_ALLOWED_ORIGINS` de produção) |

---

## 4. Seção 3 — Frontend no Navegador Real (Playwright, Tablet `1180×820`)

### 4.1 Checklist Funcional e de Interface

| Item | Status | Evidência |
|---|:---:|---|
| Login, menu e todas as 9 telas abrem sem erro de JS/API no console | **Ressalva (favicon)** | **0 erros de JavaScript (`pageerror`)** e **0 erros de API** em todas as telas (Gerente e Funcionário); único erro no console do navegador foi `HTTP 404` ao buscar `/favicon.ico` (arquivo inexistente em `frontend/`) |
| **Passo a passo + reload (RNF-C002):** passo, ingredientes marcados e temporizador persistem | **Passou** | Ingrediente marcado, temporizador do passo 1 (60 s) iniciado (`00:59`), avançou ao passo 2 e executou `page.reload()`: passo 2 mantido, checkbox mantido e temporizador do passo 1 seguiu correndo (`00:57`, `rodando=true`) |
| Temporizador chega a zero e exibe alarme na tela | **Passou** | Passo de 2 s zerou (`00:00`) e exibiu o banner fixo vermelho `#alarme`: *"Tempo esgotado no passo 2 (Descanso rápido)!"* (confirmado em print) |
| Registrar perda pela tela: peso sugerido + sem R$ para funcionário | **Passou** | Ao digitar `2` unidades em lote de 1,5 kg / 10 un, o campo `pesoKg` foi preenchido automaticamente com `0.3`; após salvar, tabela exibiu `"0,3 kg"`; **zero ocorrência de `R$`** em Execução e Calculadora do funcionário |
| **Botões e campos ≥ 48 px (RNF-U001)** via `getBoundingClientRect()` | **Parcial (2 achados menores)** | Todos os botões operacionais e campos de texto em Login, Kanban (458/458 botões), Execução, Modais e Calculadora atingiram **≥ 48 px**. Ver tabela detalhada abaixo (botões do modal de tutorial mediram `47,1 px` e `<input>` nativos de checkbox/radio medem `24–28 px` dentro de `<label>` clicáveis de `54–76,9 px`) |
| **Até 3 toques por ação operacional (RNF-U002)** | **Passou** | Iniciar OP: **1 toque** · Avançar passo: **1 toque** · Registrar perda: **2 toques** · Concluir OP: **2 toques** (todas ≤ 3) |
| **Formatação pt-BR** (números, R$, datas e horário de Brasília) | **Passou** | Dashboard exibiu `25/11/2026`, `19/11/2026 a 25/11/2026`, `60,8%`, `3 kg`, `1,5 kg`, `R$ 14,60`, `R$ 7,30`; Kanban exibiu horário de Brasília (`Entrega: 10:51`, `Atualizado às 08:51`); Execução exibiu `0,3 kg` |

### 4.2 Auditoria Detalhada de Área de Toque — `getBoundingClientRect()` (RNF-U001)

| Tela (perfil Funcionário, `1180×820`) | Controles visíveis | Elementos com largura ou altura < 48 px | Dimensão do elemento | Dimensão do `<label>` pai clicável |
|---|---:|---|---:|---:|
| Login | 4 | Nenhum | — | — |
| Início + Modal Boas-vindas (Tutorial) | 8 | Botões `"Explorar sozinho"` e `"Começar a trilha"` | `159,5 × 47,1 px` e `157,9 × 47,1 px` | — *(defeito real: −0,9 px)* |
| Kanban | 459 | `<input type="checkbox" id="so-minhas">` | `24 × 48 px` | `139,5 × 54 px` (≥ 48 px) |
| Execução / Passo a passo (e modais Perda/Concluir) | 14–20 | 2× `<input type="checkbox">` de ingredientes | `28 × 48 px` | `347,6 × 76,9 px` (≥ 48 px) |
| Calculadora | 11 | 2× `<input type="radio" name="modo">` | `24 × 48 px` | `250,2 × 56 px` e `232,1 × 56 px` (≥ 48 px) |

### 4.3 Contagem de Toques por Ação (RNF-U002)

| Ação operacional | Toques medidos | Sequência executada no Playwright | Limite (≤ 3) |
|---|---:|---|:---:|
| Iniciar uma OP | **1** | 1. Tocar em `Iniciar` no cartão da OP no Kanban | **Passou** |
| Avançar um passo | **1** | 1. Tocar em `Próximo passo →` na tela de Execução | **Passou** |
| Registrar perda | **2** | 1. Tocar em `Registrar perda` · 2. Preencher campos e tocar em `Registrar` | **Passou** |
| Concluir OP | **2** | 1. Tocar em `Concluir OP` no último passo · 2. Confirmar quantidade prevista e tocar em `Concluir OP` | **Passou** |

### 4.4 Capturas de Tela em Tablet (`1180×820`)

Capturas geradas automaticamente pelo Playwright no ambiente real da AWS (`01-login-tablet.png`, `02-dashboard-tablet.png`, `03-kanban-tablet.png`, `04-ordens-producao-tablet.png`, `05-calculadora-tablet.png`, `06-fichas-tecnicas-tablet.png`, `07-execucao-passo-a-passo-tablet.png`, `08-execucao-alarme-tablet.png`).

---

## 5. Defeitos e Melhorias Encontrados

| # | Severidade | Achado | Como reproduzir | Correção recomendada |
|---|:---:|---|---|---|
| 1 | **Média** | **Navegação completa do Kanban com 500 OPs** leva `1,60 s` quente (e `28,8 s` com Lambda fria), acima de `1,5 s` na primeira carga | Abrir `/pages/kanban/index.html` com 500 OPs no banco (tablet `1180×820`) | Servir `frontend/` via S3+CloudFront (ou habilitar HTTP cache/compressão + paginação/filtro padrão do Kanban) e ativar Lambda SnapStart |
| 2 | **Média** | **Controles de endurecimento pendentes** em produção: ausência de cabeçalhos `CSP` e `HSTS`, ausência de rate limit `429` no login e `http://localhost:5500` ainda aceito no CORS | `HEAD /`, 6× `POST /api/auth/login` inválidos e `OPTIONS /api/auth/login` com `Origin: http://localhost:5500` | Aplicar as correções já mapeadas na auditoria defensiva (CSP/HSTS no `SecurityConfig`, WAF/rate limit e remover origens `localhost` da env de produção) |
| 3 | **Baixa** | **Erro `404` no console para `/favicon.ico`** em todas as telas | Abrir qualquer tela no Chrome com DevTools → aba Console | Adicionar `frontend/favicon.ico` (ou `<link rel="icon" href="data:image/svg+xml,...">`) |
| 4 | **Baixa** | **Botões do modal de boas-vindas do tutorial** (`"Explorar sozinho"` e `"Começar a trilha"`) medem **`47,1 px`** de altura (0,9 px abaixo dos 48 px do RNF-U001) | Logar pela primeira vez como Funcionário em viewport `1180×820` e medir `getBoundingClientRect()` dos botões de `dialog[open]` | Garantir `min-height: 48px` explícito nos botões dentro do modal de tutorial (`tutorial.js` / `base.css`) |

---

## 6. Recomendações de Atualização para os Slides

### Slide 24 — Desempenho (RNF-D001 ≤ 1,5 s)
- **Substituir a referência antiga de "30 ms em H2 local"** pelos números reais medidos na AWS (`us-east-2`, Lambda Java 21 + Aurora PostgreSQL 17.9, 500 OPs):
  - **Servidor (CloudWatch, `n = 1.561`):** mediana **`19 ms`**, p95 **`51 ms`**, memória **`417–442 MB`** (25% dos `1.769 MB`).
  - **Cliente Brasil → AWS (quente, 30 repetições, já incluindo ~150 ms de rede):**
    - Kanban (500 OPs): p50 **`212 ms`** · p95 **`610 ms`** (recarga no navegador: **`463 ms`**) — **≤ 1,5 s ✓**
    - Calculadora (demanda e estoque): p50 **`164 ms`** · p95 **`171 ms`** — **≤ 1,5 s ✓**
    - Dashboard (4 indicadores paralelos): p50 **`173 ms`** · p95 **`245 ms`** — **≤ 1,5 s ✓**
    - Login (BCrypt custo 12): p50 **`542 ms`** · p95 **`549 ms`** — **≤ 1,5 s ✓**
  - **Transparência sobre início a frio (trade-off do modelo serverless gratuito/baixo custo):**
    - Lambda fria (5×): **`12,0 s`** (mediana) · Aurora pós-auto-pause `0.0 ACU` (3×): **`18,3 s`** (mediana; **0 erros 500**, sempre abaixo do timeout de `45 s`).
    - Mitigação pronta para produção contínua: **Lambda SnapStart + S3/CloudFront + Aurora `MinCapacity ≥ 0.5 ACU` em horário comercial**.

### Slide de Processo e Qualidade
- Destacar a **pirâmide completa de validação**:
  - **343 testes automatizados** no CI (`./mvnw verify`, JUnit 5 + MockMvc + `@DataJpaTest`)
  - **19 verificações automatizadas em ambiente AWS real** (clone *copy-on-write* do Aurora 17.9 + Lambda isolada): 6/6 migrações Flyway, renovação IAM após 15,8 min ocioso, concorrência real `@Version` (`200 + 409`), fuso `America/Sao_Paulo` (entrega 23:30), RBAC e isolamento financeiro (RNF-S002)
  - **Testes E2E no navegador real (Playwright, tablet `1180×820`)**: validação de persistência pós-reload (RNF-C002), alarme de temporizador, auditoria `getBoundingClientRect()` (RNF-U001) e contagem de 1–2 toques por ação (RNF-U002), com defeitos reais encontrados e documentados (`/favicon.ico` 404, 47,1 px no modal de tutorial e carga fria de estáticos).

---

## 7. Reprodução e Limpeza

- Os scripts reutilizáveis estão em [`testes-producao/`](../testes-producao/) (`funcional-seguranca.mjs`, `desempenho.mjs`, `e2e.spec.ts` e `README.md`), todos parametrizados por variáveis de ambiente e sem credenciais.
- Ao final da execução, a Lambda `A3-teste` (com sua Function URL e log group) e o clone Aurora `database-1-teste` (instância e cluster) foram removidos da conta AWS em `us-east-2`, mantendo apenas os recursos originais de produção (`A3` e `database-1`).
