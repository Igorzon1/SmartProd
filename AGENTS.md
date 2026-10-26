# AGENTS.md

Instruções para agentes de IA (Codex, Copilot, Cursor, etc.). O conteúdo completo está em **[CLAUDE.md](CLAUDE.md)** — leia-o primeiro.

Resumo do essencial:

- Projeto: **SmartProd**, sistema web de gestão de produção para padarias (Kanban de OPs, Porcentagem do Padeiro, perdas, dashboard).
- Backend: **Java 21 + Spring Boot 3.5 + Maven** em `backend/`. **Não use Node.js** no backend, apesar de PDFs antigos citarem.
- Frontend: HTML/CSS/JS puro em `frontend/`.
- Banco: PostgreSQL (prod), H2 (dev/testes), migrações Flyway.
- Documentação e requisitos em `docs/` (comece por `docs/README.md`).
- Idioma do domínio, UI, commits e docs: **português (pt-BR)**.
- Rode `cd backend && ./mvnw verify` antes de propor mudanças.
- Dúvidas abertas estão em `docs/08-questoes-em-aberto.md`: pergunte, não presuma.
