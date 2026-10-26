# ADR-0001 — Backend em Java com Spring Boot (em vez de Node.js)

- **Status:** aceita
- **Data:** 2026-10-26
- **Decisor:** Igor (membro da equipe). *Confirmar com os demais integrantes.*

## Contexto

Os documentos entregues (Requisitos v1.0, Canvas) definiam **Node.js com Express/NestJS** e "JavaScript unificado" (RNF-Comp002). O Canvas já apontava como risco o conhecimento inicial limitado em JavaScript/Node.js. Foi decidido trocar o backend para **Java com Spring Boot**.

## Decisão

- Backend em **Java 21 (LTS)** com **Spring Boot 3.5.x**, build **Maven** (wrapper).
- Persistência com Spring Data JPA, migrações **Flyway**, **PostgreSQL** em produção e **H2** em dev/testes.
- Segurança com **Spring Security**, senhas BCrypt(12).
- O frontend continua em HTML5/CSS3/JavaScript puro e consome a API REST.

## Consequências

**Positivas:** ecossistema maduro para REST, segurança (RBAC com `@PreAuthorize`), validação e testes (JUnit/MockMvc) — alinhado à exigência de testes da A3; tipagem forte para as fórmulas de dinheiro e quantidades (`BigDecimal`).

**Negativas / custos:**
- Perde-se o "JavaScript unificado": **RNF-Comp002** muda.
- Hospedagem: o Render exige **Dockerfile** para Java; Vercel/Heroku não são opções diretas para o backend.
- Consumo de memória maior que Node em planos gratuitos; *cold start* mais lento.

## O que muda nos documentos

| Documento | Item | Antes → Depois |
|---|---|---|
| Requisitos | RNF-Comp002 | "Node.js no backend, JS unificado" → "Java/Spring Boot no backend; HTML/CSS/JS no frontend" |
| Requisitos | RNF-S002 | "Middleware de autorização RBAC" → Spring Security + `@PreAuthorize` |
| Requisitos | Restrições técnicas | Linguagem e framework de backend atualizados |
| Canvas | Bloco 8 (riscos da ferramenta) | "JavaScript/Node.js" → "Java/Spring Boot" |
| Canvas | Referências de ferramentas | Tilkov & Vinoski (Node.js) → sugerir referência de Java/Spring |

Os PDFs oficiais **ainda precisam ser atualizados** pela equipe (ver Q10).

## Alternativas consideradas

- **Manter Node.js/Express:** descartado por decisão da equipe.
- **Spring Boot 4.x:** adiado; a 3.5.x tem mais material e suporte de IAs. Pode ser migrado depois.
