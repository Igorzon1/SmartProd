# 09 — Divisão de trabalho (3 duplas)

São 6 pessoas: **3 duplas, cada uma com 1 de backend e 1 de frontend**. Cada dupla é **dona** de um conjunto de pastas e só edita esses arquivos; é isso que evita conflito. As tarefas ficam no Jira (chave `KAN`) e o código chega por fork + Pull Request ([CONTRIBUTING.md](../CONTRIBUTING.md)).

## Duplas

| Dupla | Tema | Requisitos | Arquivo com as tarefas |
|---|---|---|---|
| 1 | Fundação e cadastros | RF001, RF002, RF003, RF004 | [dupla-1-fundacao-e-cadastros.md](tarefas/dupla-1-fundacao-e-cadastros.md) |
| 2 | Receitas e indicadores | RF005, RF006, RF007 e RF012–RF015 | [dupla-2-receitas-e-indicadores.md](tarefas/dupla-2-receitas-e-indicadores.md) |
| 3 | Esteira de produção | RF008, RF009, RF010, RF011 | [dupla-3-esteira-de-producao.md](tarefas/dupla-3-esteira-de-producao.md) |

A abertura de OP (RF008) fica com a dupla 3, junto do Kanban e das perdas, para que as entidades de OP e perda tenham um dono só. O dashboard fica com a dupla 2 porque só **lê** os dados da dupla 3 e é a última parte a ficar pronta.

## Quem é dono de quê

Backend em `backend/src/main/java/br/com/smartprod/` (testes em `backend/src/test/...` no mesmo pacote). Frontend em `frontend/`.

| Dupla | Pacotes do backend | Frontend |
|---|---|---|
| 1 | `usuario/`, `auth/`, `insumo/`, `config/` | `js/core/`, `css/base.css`, `index.html`, `js/home.js`, `pages/login/`, `pages/usuarios/`, `pages/insumos/` |
| 2 | `receita/`, `dashboard/` | `pages/fichas-tecnicas/`, `pages/calculadora/`, `pages/dashboard/` |
| 3 | `producao/`, `perda/` | `pages/ordens-producao/`, `pages/kanban/`, `pages/execucao/` |

Cada pacote tem um `package-info.java` dizendo quem é o dono. Cada página tem seu `index.html` e `main.js`; CSS ou arquivos extras da página ficam **dentro da própria pasta** da página.

## Arquivos compartilhados (cuidado)

| Arquivo | Regra |
|---|---|
| `backend/pom.xml` | Nova dependência: PR separado e pequeno, avisando o grupo |
| `backend/src/main/resources/application*.yml` | Idem |
| `config/SecurityConfig.java` | Só a dupla 1. Os demais usam `@PreAuthorize` nos controllers |
| `db/migration/` | Cada um cria arquivo **novo**, nomeado `V<aaaaMMddHHmm>__descricao.sql`. Nunca edite um já mergeado |
| `frontend/js/core/*`, `css/base.css` | Dona: dupla 1. As outras duplas só usam; pedido de mudança vira PR da dupla 1 |
| `docs/`, `CLAUDE.md` | Igor. Sugestões por PR |

## Contratos entre as duplas

São **propostas**: a dupla dona pode ajustar, mas deve atualizar este arquivo e avisar quem consome. Datas em ISO-8601 UTC; dinheiro e quantidades em `BigDecimal` (número no JSON). Erros em `application/problem+json`, com `title` e `detail` em português (mostre o `detail` na tela); no 400 de validação, `campos` traz `{ "campo": "mensagem" }`.

### Autenticação (dupla 1 → todos)

```
POST /api/auth/login
{ "email": "gerente@padaria.com", "senha": "..." }
→ 200 { "token": "<jwt>", "tipo": "Bearer", "expiraEm": "2026-11-01T12:00:00Z",
        "usuario": { "id": 1, "nome": "Maria", "perfil": "GERENTE" } }
→ 401 se e-mail/senha inválidos ou usuário inativo
```

Demais chamadas levam `Authorization: Bearer <token>`. Perfis viram `ROLE_GERENTE` e `ROLE_FUNCIONARIO`; use `@PreAuthorize("hasRole('GERENTE')")`. Sem token → 401; perfil sem permissão → 403. *(JWT decidido em 2026-11-04; ver Q3 em [08](08-questoes-em-aberto.md).)*

### Usuários (dupla 1 → dupla 3, só `GERENTE`)

```
GET   /api/usuarios?ativo=true      → [ { "id": 3, "nome": "Beto", "email": "beto@padaria.com", "perfil": "FUNCIONARIO",
                                          "ativo": true, "criadoEm": "2026-11-04T12:00:00Z" } ]
POST  /api/usuarios                 { "nome", "email", "senha" (8 a 72 caracteres), "perfil" }
PUT   /api/usuarios/{id}            { "nome", "email", "perfil", "senha" (opcional) }
PATCH /api/usuarios/{id}/inativar   (não vale para a própria conta)
PATCH /api/usuarios/{id}/reativar
```

### Insumos (dupla 1 → dupla 2)

```
GET /api/insumos        → [ { "id": 1, "nome": "Farinha de trigo", "unidadeMedida": "KG", "custoUnitario": 6.50 } ]
GET /api/insumos/{id}
POST /api/insumos · PUT /api/insumos/{id} · PATCH /api/insumos/{id}/inativar · PATCH /api/insumos/{id}/reativar   (GERENTE)
```

Para `FUNCIONARIO` o campo `custoUnitario` **não existe** na resposta.

`GET /api/insumos` devolve só insumos **ativos** (o Gerente pode usar `?incluirInativos=true` e recebe também `ativo`); `GET /api/insumos/{id}` devolve o insumo mesmo se inativo, para fichas antigas. `custoUnitario` aceita até 4 casas decimais (o custo por grama ou ml é muito pequeno) e é o custo de **uma** `unidadeMedida` (`G`, `KG`, `ML`, `L` ou `UN`).

### Fichas técnicas e cálculo (dupla 2 → dupla 3)

```
GET /api/fichas-tecnicas            → [ { "id": 1, "nome": "Pão francês", "rendimentoPadrao": 50 } ]
GET /api/fichas-tecnicas/{id}
→ { "id": 1, "nome": "Pão francês", "rendimentoPadrao": 50,
    "ingredientes": [ { "insumoId": 1, "insumoNome": "Farinha de trigo", "unidadeMedida": "G",
                        "quantidadeBase": 1000, "ehFarinha": true, "percentualPadeiro": 100.0 } ],
    "passos": [ { "ordem": 1, "etapa": "Mistura", "descricao": "Misture tudo", "duracaoSegundos": 600 } ] }

GET /api/fichas-tecnicas/{id}/calculo?unidades=120
→ { "fichaId": 1, "unidades": 120, "fator": 2.4,
    "ingredientes": [ { "insumoId": 1, "nome": "Farinha de trigo", "unidadeMedida": "G",
                        "quantidade": 2400, "percentualPadeiro": 100.0 } ] }

POST /api/fichas-tecnicas/{id}/calculo-por-estoque
{ "estoque": [ { "insumoId": 1, "quantidade": 5000 } ] }
→ { "unidadesPossiveis": 250, "ingredienteLimitante": { "insumoId": 1, "nome": "Farinha de trigo" },
    "ingredientes": [ ... ] }
```

Sem custos para `FUNCIONARIO`.

Escrita das fichas (**proposta, ainda não implementada**; é o formato que a tela de fichas já usa): `POST /api/fichas-tecnicas` e `PUT /api/fichas-tecnicas/{id}` com `{ "nome", "rendimentoPadrao", "ingredientes": [ { "insumoId", "quantidadeBase", "ehFarinha" } ], "passos": [ { "ordem", "etapa", "descricao", "duracaoSegundos" } ] }`.

### Ordens de produção e perdas (dupla 3 → dupla 2 e frontends)

```
POST  /api/ordens-producao                  (GERENTE)
{ "fichaTecnicaId": 1, "quantidadeUnidades": 120, "horarioEntrega": "2026-10-28T09:00:00Z", "responsavelId": 3 }

GET   /api/ordens-producao?status=PENDENTE  → [ { "id": 10, "fichaTecnicaId": 1, "fichaNome": "Pão francês",
        "quantidadeUnidades": 120, "horarioEntrega": "...", "status": "PENDENTE",
        "responsavel": { "id": 3, "nome": "Beto" } } ]
PATCH /api/ordens-producao/{id}/iniciar     (PENDENTE → EM_ANDAMENTO)
PATCH /api/ordens-producao/{id}/concluir    { "quantidadeProduzida": 118 }   (EM_ANDAMENTO → CONCLUIDA)
GET   /api/ordens-producao/{id}/execucao
→ { "ordemProducaoId": 10, "fichaTecnicaId": 1, "fichaNome": "Pão francês", "quantidadeUnidades": 120, "status": "EM_ANDAMENTO",
    "ingredientes": [ { "insumoId": 1, "nome": "Farinha de trigo", "quantidade": 2400.000, "unidadeMedida": "G" } ],
    "passos": [ { "ordem": 1, "etapa": "Mistura", "descricao": "...", "duracaoSegundos": 600 } ] }
POST  /api/ordens-producao/{id}/perdas      { "etapa": "Forno", "pesoKg": 2.5, "unidades": 10, "motivo": "Fornada queimada" }
GET   /api/ordens-producao/{id}/perdas      → [ { "id", "etapa", "pesoKg", "unidades", "motivo", "registradoPorNome", "registradoEm", ... } ]
```

- Transição inválida → 409. Perda só em OP `EM_ANDAMENTO`. Se duas pessoas alteram a mesma OP ao mesmo tempo, a segunda recebe 409.
- O responsável pode ser qualquer usuário ativo (Gerente ou Funcionário).
- `?data=AAAA-MM-DD` filtra pelo dia de entrega em São Paulo. Sem filtros, a lista traz as OPs pendentes e em andamento e as concluídas hoje (é o Kanban).
- Perda (Q12): sempre do produto da OP, em kg; `unidades` é opcional; o valor em R$ não fica na perda. Produzido (Q13): `quantidadeProduzida` é obrigatória ao concluir.

### Dashboard (dupla 2, só `GERENTE`)

```
GET /api/dashboard/rendimento?data=2026-10-28        → { "previsto": 300, "produzido": 288, "rendimentoPercentual": 96.0 }
GET /api/dashboard/desperdicio?de=...&ate=...        → { "totalKg": 12.5, "totalReais": 84.30 }
GET /api/dashboard/ranking-desperdicio?de=...&ate=... → [ { "fichaId": 1, "produto": "Pão francês", "totalKg": 5.2, "totalReais": 30.10 } ]
GET /api/dashboard/status-ops                         → { "pendentes": 4, "emAndamento": 2, "concluidas": 9 }
```

O dashboard lê as tabelas `ordens_producao` e `registros_perda` **só para leitura**, com consultas dentro do pacote `dashboard/`.

- Datas em `AAAA-MM-DD`, no dia de São Paulo; `de` e `ate` entram no período. Sem período, vale a última semana (hoje e os 6 dias anteriores); sem `data`, vale hoje.
- Rendimento: previsto = soma das OPs com entrega no dia; produzido = soma das concluídas; `rendimentoPercentual` é `null` quando não há nada previsto.
- R$ da perda = kg perdidos × custo por kg da receita (ver Q7 em [08](08-questoes-em-aberto.md)); `totalReais` é `null` quando a receita não tem custo por kg (só ingredientes em `UN`).
- Ranking: os 3 produtos com mais kg perdidos. Status: pendentes e em andamento contam todas; concluídas, só as de hoje.

## Ordem de trabalho

**Fase 1 — destrava todo mundo**
- Dupla 1: login e token, autorização por perfil, base do front (`js/core/`).
- Dupla 2: **primeiro** um PR só com a migração e as entidades de ficha técnica, mais os `GET`.
- Dupla 3: depois do PR de fichas mergeado, migração e entidades de OP e de perda (a OP tem chave estrangeira para a ficha).

**Fase 2**
- Dupla 1: usuários, insumos e as telas.
- Dupla 2: Porcentagem do Padeiro, calculadora e as telas.
- Dupla 3: Kanban, passo a passo, perdas e as telas.

**Fase 3**
- Dupla 2: dashboard (depende das tabelas da dupla 3).
- Todos: integração entre as partes, testes, ajustes e revisão da documentação.

Quem faz o frontend pode começar **no primeiro dia** usando dados falsos (arquivos JSON em `pages/<pagina>/mock/`) baseados nos contratos acima, e trocar pela API real quando o backend estiver pronto.

## Pronto quando

- **Backend:** `./mvnw verify` passa com testes novos (unitários para regras, MockMvc para endpoints), endpoint no Swagger, índices nas colunas de consulta (RNF-D002), `FUNCIONARIO` sem custos.
- **Frontend:** tela funciona em tamanho de tablet, botões ≥ 48 px na interface do Funcionário, sem erros no console, nenhum valor em R$ para o Funcionário.
- PR aprovado pelo Igor e card movido para Concluído no Jira.
