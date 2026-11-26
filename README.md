# SmartProd

![Backend CI](https://github.com/Igorzon1/SmartProd/actions/workflows/backend-ci.yml/badge.svg)
![Frontend CI](https://github.com/Igorzon1/SmartProd/actions/workflows/frontend-ci.yml/badge.svg)

Sistema web de **gestão de produção para padarias e confeitarias** pequenas e médias. Transforma a cozinha artesanal em uma **esteira guiada**: ficha técnica com **Porcentagem do Padeiro**, calculadora proporcional por demanda ou estoque disponível, **Kanban de Ordens de Produção (OPs)** com passo a passo e temporizadores, **registro de perdas/refugos** e **dashboard** de rendimento e desperdício para o gerente.

Projeto A3 da UC HUB (Ânima). Equipe: Ana, Breno, Giovanne, Igor, Ledson e Marcelo.

---

## Funcionalidades por perfil

O controle de acesso separa rigorosamente gestão financeira e operação na cozinha (**RNF-S002**):

| Perfil | O que acessa |
|---|---|
| **Gerente** | **Dashboard** (rendimento do dia, desperdício em kg e R$, Top 3 produtos com maior perda, status das OPs), **Insumos** (com custo unitário e calculadora pelo preço da embalagem), **Fichas técnicas** (Porcentagem do Padeiro, custo por ingrediente/unidade/kg e modo de preparo), **Usuários** (cadastro, edição, inativação/reativação), **Ordens de produção** (abrir e cancelar OPs pendentes), **Calculadora**, **Kanban** e **Execução**. |
| **Funcionário** | Interface simplificada para tablet touchscreen na cozinha (**botões ≥ 48 px**, **≤ 3 cliques por ação**): **Kanban** (iniciar e concluir OPs), **Execução passo a passo** (ingredientes ajustados ao lote, temporizadores com alarme e progresso salvo em `localStorage` mesmo após recarregar a página), **Registro de perdas** (com sugestão automática de peso pelas unidades) e **Calculadora**. **Nunca visualiza custos nem valores em R$.** |

---

## Arquitetura e stack

| Camada | Desenvolvimento local (`dev`) | Produção (AWS `us-east-2`) |
|---|---|---|
| **Backend** | Java 21 (LTS) + Spring Boot 3.5.16 + Maven Wrapper (`mvnw`) | AWS Lambda (`java21`, `aws-serverless-java-container-springboot3`) + Function URL HTTPS |
| **Banco de dados** | H2 em memória (modo PostgreSQL) + migrações Flyway | Amazon Aurora PostgreSQL 17.9 Serverless v2 com autenticação IAM (SigV4 + TLS) |
| **Frontend** | HTML5 + CSS3 + JavaScript puro (ES modules, sem build) | Embutido no pacote de deploy (`static/`) servido na mesma origem em UTF-8 |
| **Segurança** | Spring Security stateless, BCrypt (custo 12), JWT HS256, rate limit 429, logout com revogação, CSP + HSTS | Role IAM de menor privilégio, `JWT_SECRET` em SSM Parameter Store (`SecureString`), CORS restrito à URL oficial |
| **Qualidade** | JUnit 5, AssertJ, Mockito, MockMvc, `@DataJpaTest` (`352` testes no CI) | Suíte reproduzível de API, desempenho (500 OPs) e E2E Playwright tablet em [`testes-producao/`](testes-producao/) |

Detalhes técnicos completos em [docs/06-arquitetura.md](docs/06-arquitetura.md) e nas ADRs [ADR-0001](docs/decisoes/0001-backend-java-spring-boot.md) e [ADR-0002](docs/decisoes/0002-deploy-aws-lambda-aurora.md).

---

## Como rodar em qualquer máquina (Windows, Linux e macOS)

Você **não precisa instalar Maven nem PostgreSQL**: o projeto usa o **Maven Wrapper** (`mvnw` / `mvnw.cmd`) e sobe um banco **H2 em memória** no perfil `dev` com todas as migrações Flyway aplicadas automaticamente.

### Pré-requisitos mínimos
- **Git**
- **JDK 21** (ou superior compatível; o `pom.xml` compila para alvo Java 21). Verifique com `java -version`.
- Para servir o frontend localmente: **Node.js** (`npx serve`) **ou** **Python 3** (`python -m http.server`) **ou** a extensão *Live Server* do VS Code.

### 1. Clonar o repositório

```bash
git clone https://github.com/Igorzon1/SmartProd.git
cd SmartProd
```

### 2. Subir o backend (com dados de demonstração prontos)

O perfil `dev,demo` já inicia o sistema preenchido com insumos, 4 fichas técnicas, funcionários, ordens de produção de hoje e das últimas semanas e registros de perdas (como o banco é em memória, cada reinício volta aos dados originais):

**Windows (PowerShell):**
```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"
```

**Linux / macOS:**
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo
```

> Quer subir com o banco limpo (apenas o gerente inicial de exemplo)? Use `.\mvnw.cmd spring-boot:run` (Windows) ou `./mvnw spring-boot:run` (Linux/macOS).

Quando a API subir em `http://localhost:8080`:
- **Saúde da aplicação:** `http://localhost:8080/actuator/health` → `{"status":"UP"}`
- **Documentação interativa (Swagger UI, apenas fora de produção):** `http://localhost:8080/swagger-ui.html`

> Porta `8080` ocupada? Defina `PORT=8081` antes de rodar e ajuste `apiUrl` em `frontend/js/core/config.js`.

### 3. Servir o frontend (em outro terminal)

O frontend usa módulos ES (`<script type="module">`), portanto deve ser aberto por HTTP (e não direto por `file://`). A porta `5500` já vem liberada no CORS do perfil `dev`:

**Com Node.js (Windows, Linux ou macOS):**
```bash
npx serve -l 5500 frontend
```

**Ou com Python 3 (Windows, Linux ou macOS):**
```bash
python -m http.server 5500 --directory frontend
```

Acesse **`http://localhost:5500`** no navegador.

### 4. Credenciais de demonstração (somente ambiente local `dev` / `demo`)

| Perfil | E-mail | Onde conferir a senha local |
|---|---|---|
| **Gerente** | `gerente@smartprod.dev` | `backend/src/main/resources/application-dev.yml` |
| **Funcionários** *(perfil `demo`)* | `beto@smartprod.dev`, `carla@smartprod.dev`, `diego@smartprod.dev` | `backend/src/main/java/br/com/smartprod/config/DadosDemonstracao.java` |

> Estas contas existem **exclusivamente** no banco H2 em memória local (`dev`/`demo`) e ficam desativadas em produção.

---

## Como rodar os testes

**Backend (build + todos os testes unitários e de integração):**

Windows (PowerShell):
```powershell
cd backend
.\mvnw.cmd -B verify
```

Linux / macOS:
```bash
cd backend
./mvnw -B verify
```

**Frontend (verificação de sintaxe ES modules de todos os arquivos `.js`, igual ao CI):**

Linux / macOS:
```bash
find frontend -name '*.js' -print0 | xargs -0 -n1 sh -c 'node --input-type=module --check < "$1"' _
```

Windows (PowerShell):
```powershell
Get-ChildItem frontend -Recurse -Filter "*.js" | ForEach-Object { Get-Content $_.FullName -Raw | node --input-type=module --check }
```

**Testes no ambiente real AWS e E2E Playwright (tablet `1180×820`):** consulte [`testes-producao/README.md`](testes-producao/README.md) e os resultados medidos na AWS em [`docs/testes-em-producao.md`](docs/testes-em-producao.md).

---

## Estrutura do repositório

```text
backend/            API Spring Boot 3.5 (pacote raiz br.com.smartprod) e scripts de pacote Lambda
frontend/           Interface web estática (HTML5 + CSS3 + JS ES modules)
docs/               Documentação completa (requisitos, arquitetura, domínio, ADRs e relatório AWS)
testes-producao/    Scripts reutilizáveis de API, desempenho (500 OPs) e Playwright (tablet)
.github/workflows/  Esteiras de CI (Backend verify + Frontend sintaxe-js)
CLAUDE.md           Guia de convenções e regras do projeto
```

## Documentação

Índice completo em [docs/README.md](docs/README.md):
- Visão e escopo: [docs/01-visao-e-escopo.md](docs/01-visao-e-escopo.md)
- Requisitos (RF001–RF015 e RNFs): [docs/02-requisitos.md](docs/02-requisitos.md)
- Arquitetura atual (local e AWS): [docs/06-arquitetura.md](docs/06-arquitetura.md)
- Domínio e fórmulas: [docs/07-dominio.md](docs/07-dominio.md)
- Decisões de arquitetura (ADRs): [ADR-0001 (Java/Spring Boot)](docs/decisoes/0001-backend-java-spring-boot.md) · [ADR-0002 (AWS Lambda + Aurora)](docs/decisoes/0002-deploy-aws-lambda-aurora.md)
- Testes e desempenho reais na AWS: [docs/testes-em-producao.md](docs/testes-em-producao.md)

> Os PDFs acadêmicos originais ainda citam Node.js; o backend foi alterado para Java/Spring Boot ([ADR-0001](docs/decisoes/0001-backend-java-spring-boot.md)) e o deploy para AWS Lambda + Aurora ([ADR-0002](docs/decisoes/0002-deploy-aws-lambda-aurora.md)).

## Como contribuir

O desenvolvimento é organizado em 3 duplas (ver [docs/09-divisao-de-tarefas.md](docs/09-divisao-de-tarefas.md)) usando **fork + Pull Request** e tarefas no Jira (chave `KAN`). Consulte [CONTRIBUTING.md](CONTRIBUTING.md).
