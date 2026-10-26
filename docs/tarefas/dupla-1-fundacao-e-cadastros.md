# Dupla 1 — Fundação e cadastros

**Responsáveis:** Backend: ____________ · Frontend: ____________

## Objetivo

Entregar o **login e os perfis** (todos os outros dependem disso), o cadastro de **usuários** e de **insumos**, e a **base do frontend** (cliente da API, sessão, menu e estilos) que as outras duas duplas usam.

## Requisitos

- **RF001** Autenticar por e-mail e senha, direcionando o acesso conforme o perfil (Gerente ou Funcionário).
- **RF002** O gerente cadastra, edita, inativa e consulta usuários/colaboradores.
- **RF003** Interface simplificada para o funcionário, com botões amplos, para touchscreen.
- **RF004** Cadastrar insumos com nome, unidade de medida (g, kg, ml, L, un) e custo unitário.
- **RNF-S001** Senhas com BCrypt (já configurado em `SecurityConfig`, força 12). **RNF-S002** Dados financeiros só para Gerente. **RNF-U001** Botões ≥ 48 px, alto contraste. **RNF-D002** Índices nas colunas de consulta.

## Antes de começar

Leia [CLAUDE.md](../../CLAUDE.md), [docs/09](../09-divisao-de-tarefas.md) (donos e contratos), [docs/02 §2.1–2.2](../02-requisitos.md), [docs/06 §5](../06-arquitetura.md) e [CONTRIBUTING.md](../../CONTRIBUTING.md).

## Seus arquivos

- **Backend:** `usuario/`, `auth/` (novo), `insumo/`, `config/` em `backend/src/main/java/br/com/smartprod/`, os testes correspondentes e migrações novas.
- **Frontend:** `js/core/`, `css/base.css`, `index.html`, `js/home.js`, `pages/login/`, `pages/usuarios/`, `pages/insumos/`.

**Não mexa** em `receita/`, `dashboard/`, `producao/`, `perda/` nem em `pages/` das outras duplas.

## O que você entrega aos outros

- **Login, token e `@PreAuthorize`** — prioridade máxima, entregue primeiro (todos dependem).
- `GET /api/insumos` e `GET /api/insumos/{id}` (dupla 2).
- `js/core/` (`api.js`, `auth.js`, `menu.js`, `config.js`) e `css/base.css` para os três frontends.

## Atenção

- Hoje `application.yml` exclui `UserDetailsServiceAutoConfiguration` para não imprimir senha aleatória; remova essa exclusão quando criar o `UserDetailsService` real.
- Nunca devolva `senhaHash` na API nem escreva senha em log. Segredo do JWT por variável de ambiente, nunca no repositório.
- Seed do primeiro Gerente: só no perfil `dev`; em produção, por variável de ambiente. Combine com o Igor.
- Frontend: enquanto o login real não existe, `config.js` tem `usarSessaoFalsa: true` para as páginas abrirem. Troque para `false` quando o login estiver integrado.

## Tarefas

| Código | Tarefa | Requisitos | Prioridade | Depende de |
|---|---|---|---|---|
| D1-BE-01 | Login e token de acesso | RF001, RNF-S001 | Alta | - |
| D1-BE-02 | Autorização por perfil | RF001, RNF-S002 | Alta | D1-BE-01 |
| D1-BE-03 | CRUD de usuários | RF002 | Alta | D1-BE-02 |
| D1-BE-04 | CRUD de insumos | RF004, RNF-D002 | Alta | D1-BE-02 |
| D1-FE-01 | Base do front: API, sessão e menu | RF001, RNF-S002 | Alta | - |
| D1-FE-02 | Tela de login | RF001 | Alta | D1-FE-01 |
| D1-FE-03 | Tela de usuários | RF002 | Alta | D1-FE-01, D1-BE-03 |
| D1-FE-04 | Tela de insumos | RF004 | Alta | D1-FE-01, D1-BE-04 |
| D1-FE-05 | Estilo base e interface do Funcionário | RF003, RNF-U001 | Alta | - |

### D1-BE-01 — Login e token de acesso

- **Parte:** backend · **Requisitos:** RF001, RNF-S001 · **Prioridade:** Alta
- **Depende de:** -
- **Arquivos:** `backend/src/main/java/br/com/smartprod/auth/`, `backend/src/main/java/br/com/smartprod/usuario/`
- **O que fazer:** Criar `POST /api/auth/login` (e-mail e senha) que devolve token JWT e os dados do usuário (id, nome, perfil), conforme o contrato do docs/09. Implementar o `UserDetailsService` sobre `Usuario` (só usuários ativos) e conferir a senha com BCrypt. Remover a exclusão de `UserDetailsServiceAutoConfiguration` do `application.yml`. Segredo e validade do token vêm de variáveis de ambiente. Criar o primeiro Gerente apenas no perfil dev (combinar com o Igor). Testes: login válido, senha errada (401) e usuário inativo (401).
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D1-BE-02 — Autorização por perfil

- **Parte:** backend · **Requisitos:** RF001, RNF-S002 · **Prioridade:** Alta
- **Depende de:** D1-BE-01
- **Arquivos:** `backend/src/main/java/br/com/smartprod/auth/`, `backend/src/main/java/br/com/smartprod/config/`
- **O que fazer:** Filtro que lê o token Bearer e autentica a requisição; perfis viram `ROLE_GERENTE` e `ROLE_FUNCIONARIO`. Ajustar o `SecurityConfig` (único lugar onde ele muda) e documentar o uso de `@PreAuthorize("hasRole('GERENTE')")` para as outras duplas. Testes: sem token 401, perfil errado 403, token válido 200.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D1-BE-03 — CRUD de usuários

- **Parte:** backend · **Requisitos:** RF002 · **Prioridade:** Alta
- **Depende de:** D1-BE-02
- **Arquivos:** `backend/src/main/java/br/com/smartprod/usuario/`
- **O que fazer:** Endpoints `/api/usuarios` (só Gerente): criar, editar, listar/consultar e inativar (`PATCH /{id}/inativar`; nunca remover). Nunca devolver `senhaHash`. E-mail duplicado devolve 409. Senha gravada com BCrypt. Testes unitários do serviço e de integração com MockMvc.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D1-BE-04 — CRUD de insumos

- **Parte:** backend · **Requisitos:** RF004, RNF-D002 · **Prioridade:** Alta
- **Depende de:** D1-BE-02
- **Arquivos:** `backend/src/main/java/br/com/smartprod/insumo/`, `backend/src/main/resources/db/migration/`
- **O que fazer:** Entidade `Insumo` (nome, unidadeMedida G|KG|ML|L|UN, custoUnitario `BigDecimal`, ativo), migração Flyway com índice no nome e endpoints `/api/insumos`. Gerente: CRUD completo. Funcionário: só leitura e SEM `custoUnitario` (DTO diferente). A dupla 2 usa `GET /api/insumos` e `GET /api/insumos/{id}`: publique a migração e a leitura primeiro.
- **Pronto quando:** `./mvnw verify` passa com testes novos (unitários para regras e MockMvc para endpoints); endpoint aparece no Swagger; respeita o perfil (RNF-S002); PR aprovado pelo Igor.

### D1-FE-01 — Base do front: API, sessão e menu

- **Parte:** frontend · **Requisitos:** RF001, RNF-S002 · **Prioridade:** Alta
- **Depende de:** -
- **Arquivos:** `frontend/js/core/`, `frontend/index.html`, `frontend/js/home.js`
- **O que fazer:** Revisar e evoluir `js/core/` (`api.js`, `auth.js`, `menu.js`, `config.js`): é a base dos três frontends, e só a dupla 1 altera. Guarda de rota por perfil, logout e tratamento de 401/403. Quando o login real existir, trocar `usarSessaoFalsa` para `false` em `config.js`. Documentar no próprio código como as outras duplas devem usar.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.

### D1-FE-02 — Tela de login

- **Parte:** frontend · **Requisitos:** RF001 · **Prioridade:** Alta
- **Depende de:** D1-FE-01
- **Arquivos:** `frontend/pages/login/`
- **O que fazer:** `pages/login`: formulário de e-mail e senha que chama `POST /api/auth/login`, salva a sessão e leva à página inicial conforme o perfil. Mensagens de erro claras e uso por teclado. Use dados falsos até o backend estar pronto.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.

### D1-FE-03 — Tela de usuários

- **Parte:** frontend · **Requisitos:** RF002 · **Prioridade:** Alta
- **Depende de:** D1-FE-01, D1-BE-03
- **Arquivos:** `frontend/pages/usuarios/`
- **O que fazer:** `pages/usuarios` (só Gerente): listar, cadastrar, editar e inativar usuários, com confirmação antes de inativar. Nunca exibir a senha.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.

### D1-FE-04 — Tela de insumos

- **Parte:** frontend · **Requisitos:** RF004 · **Prioridade:** Alta
- **Depende de:** D1-FE-01, D1-BE-04
- **Arquivos:** `frontend/pages/insumos/`
- **O que fazer:** `pages/insumos` (Gerente): lista, cadastro e edição de insumos com unidade de medida e custo unitário.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.

### D1-FE-05 — Estilo base e interface do Funcionário

- **Parte:** frontend · **Requisitos:** RF003, RNF-U001 · **Prioridade:** Alta
- **Depende de:** -
- **Arquivos:** `frontend/css/base.css`
- **O que fazer:** `css/base.css`: botões com no mínimo 48 px, alto contraste, fonte grande e componentes reutilizáveis (botão, card, formulário, tabela, aviso). Documentar as classes no próprio arquivo para as outras duplas. Layout responsivo para tablet.
- **Pronto quando:** a tela funciona no navegador e em tamanho de tablet; botões ≥ 48 px; sem erros no console; nenhum valor em R$ para o Funcionário; PR aprovado pelo Igor.


## Como testar

```bash
cd backend && ./mvnw verify
```

Frontend: `npx serve -l 5500 frontend` e abra http://localhost:5500.

## Prompt para sua IA

> Leia CLAUDE.md, docs/09-divisao-de-tarefas.md e docs/tarefas/dupla-1-fundacao-e-cadastros.md. Sou da dupla 1 (parte: backend/frontend). Trabalhe só nos arquivos da minha dupla, escreva testes e rode ./mvnw verify antes de terminar.
