# ADR-0002 — Deploy em AWS Lambda (Java 21) + Amazon Aurora PostgreSQL Serverless v2

- **Status:** aceita
- **Data:** 2026-11-26
- **Decisor:** Igor e equipe SmartProd

## Contexto

Após a adoção de Java 21 + Spring Boot 3.5 ([ADR-0001](0001-backend-java-spring-boot.md)), o projeto precisava de uma hospedagem em nuvem acessível por HTTPS para demonstração e operação em tablets, com banco PostgreSQL gerenciado e custo controlado dentro do plano gratuito (*Free Tier*) da AWS.

As opções citadas nos documentos iniciais (Render com Dockerfile + Vercel + banco PostgreSQL de terceiros) introduziam múltiplas plataformas separadas, necessidade de gerenciar Dockerfile, CORS entre domínios diferentes e limites mais restritos de hibernação.

## Decisão

Hospedar o SmartProd integralmente na **AWS (região `us-east-2`)** usando arquitetura *serverless*:

- **Computação e entrega web:** **AWS Lambda** (`java21`, `x86_64`, `1.769 MB`, timeout `60 s`) com `aws-serverless-java-container-springboot3` e **Lambda Function URL** (HTTPS nativo).
- **Inicialização *lazy*:** `StreamLambdaHandler` inicializa o contexto Spring Boot na primeira invocação `handleRequest` (evitando estourar o limite de 10 s da fase `INIT` da Lambda quando o banco desperta de *auto-pause*) e aplica `defaultContentCharset("UTF-8")`.
- **Frontend embutido na mesma origem:** `backend/scripts/build-lambda.ps1` embute `frontend/` em `static/` dentro de `smartprod-lambda.zip` (~36 MB), com `StaticFrontendConfig` garantindo `charset=UTF-8` em ativos textuais e encaminhamento `/` → `/index.html`.
- **Banco de dados:** **Amazon Aurora PostgreSQL 17.9 Serverless v2** (`MinCapacity: 0.0 ACU`, `MaxCapacity: 4.0 ACU`, `SecondsUntilAutoPause: 300 s`) com **autenticação IAM** (`IamDataSourceConfig` em Java 21 puro com SigV4 + TLS `sslmode=require`, eliminando senha fixa de banco).
- **Segurança e observabilidade:** role IAM de menor privilégio (`SmartProdLambdaA3Role`), `JWT_SECRET` armazenado como `SecureString` no **AWS SSM Parameter Store** (lido via `SsmParameterClient` SigV4 puro sem adicionar JARs do AWS SDK), `DeletionProtection` ativada no Aurora, retenção de 30 dias em CloudWatch Logs e alarmes de `Errors` e `Throttles`.

## Consequências

**Positivas:**
- **Custo quase zero em ociosidade:** Lambda cobra apenas por execução e Aurora Serverless v2 pausa automaticamente para `0.0 ACU` após 5 min sem consultas.
- **Sem senhas fixas em banco nem em variáveis de ambiente:** IAM DB Auth gera tokens de 15 min renovados automaticamente pelo pool Hikari (`maxLifetime = 9 min`), e o segredo JWT fica cifrado no SSM Parameter Store.
- **Desempenho quente validado acima da meta RNF-D001 (≤ 1,5 s):** medições reais com 500 OPs na AWS (`us-east-2`, [testes-em-producao.md](../testes-em-producao.md)) mostraram servidor quente com mediana de `19 ms` (p95 `51 ms`, memória `417–442 MB`), Kanban cliente Brasil → Ohio em p50 `212 ms` / p95 `610 ms` (recarga no tablet `463 ms`) e calculadora em p95 `171 ms`.
- **Origem única HTTPS:** simplifica CORS e evita problemas entre frontend e backend.

**Negativas / trade-offs:**
- **Início a frio (*cold start*):** ~12 s na Lambda fria e ~18 s no despertar do Aurora a partir de `0.0 ACU` (sempre abaixo do timeout de `45 s` do frontend e sem erros `500`, mas perceptível no primeiro acesso após pausa).
- **Região única (`us-east-2`):** a conta AWS do projeto opera em modelo *single-region* em Ohio (~150 ms de latência de rede a partir do Brasil).
- **Limitação do Free Tier para retenção de backup no Aurora:** `BackupRetentionPeriod` fica limitado a `1` dia no plano gratuito (ampliação para 7+ dias requer upgrade de plano).

**Mitigações previstas para operação contínua em produção:**
1. Ativar **AWS Lambda SnapStart** (Java 21) para reduzir o *cold start* da JVM de ~12 s para ~1–2 s.
2. Servir `frontend/` por **Amazon S3 + CloudFront** para evitar invocações frias em paralelo de arquivos estáticos.
3. Ajustar o Aurora para `MinCapacity = 0.5 ACU` durante o horário comercial da padaria, eliminando pausas durante o expediente.

## Alternativas consideradas

- **Render (Docker) + Vercel + PostgreSQL externo:** descartado por fragmentar a infraestrutura em três provedores, exigir Dockerfile e complicar credenciais e CORS.
- **Amazon ECS Fargate / EC2 dedicado:** descartado para o escopo da A3 por gerar custo fixo 24×7 mesmo sem tráfego.
- **AWS Secrets Manager para `JWT_SECRET`:** substituído por **SSM Parameter Store (`SecureString` padrão)** por oferecer criptografia KMS equivalente sem custo mensal fixo por segredo.
