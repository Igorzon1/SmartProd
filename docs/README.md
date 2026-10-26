# Documentação do SmartProd

Índice da documentação. Os arquivos numerados são as **versões em Markdown** dos PDFs entregues na UC HUB, organizadas para leitura rápida por pessoas e por IAs. Onde o projeto mudou depois dos PDFs, o texto traz uma marca **ALTERADO** com o motivo.

## Ordem de leitura sugerida

| # | Arquivo | Conteúdo | Origem |
|---|---|---|---|
| 1 | [01-visao-e-escopo.md](01-visao-e-escopo.md) | Problema, público, solução, escopo e fora de escopo | Visão e Escopo (16/10/2026) |
| 2 | [02-requisitos.md](02-requisitos.md) | RF001–RF015, RNF, restrições, critérios de aceitação, glossário | Requisitos do Projeto (17/10/2026) |
| 3 | [03-pre-projeto.md](03-pre-projeto.md) | Concorrentes, stakeholders, fluxos, telas previstas | Pré-Projeto UC HUB |
| 4 | [04-canvas-uc-hub.md](04-canvas-uc-hub.md) | Canvas de 12 blocos e referências científicas | Canvas UC HUB |
| 5 | [05-regras-a3.md](05-regras-a3.md) | Regras, prazos e entregas obrigatórias da A3 | Comunicado do professor |
| 6 | [06-arquitetura.md](06-arquitetura.md) | Stack, camadas, estrutura, API, segurança, deploy | **Novo** |
| 7 | [07-dominio.md](07-dominio.md) | Entidades, fórmulas e regras de negócio | **Novo** (derivado dos requisitos) |
| 8 | [08-questoes-em-aberto.md](08-questoes-em-aberto.md) | Inconsistências e decisões pendentes | **Novo** |
| 9 | [09-divisao-de-tarefas.md](09-divisao-de-tarefas.md) | 3 duplas, donos de cada pasta, contratos entre as duplas, ordem de trabalho | **Novo** |
| — | [tarefas/](tarefas/) | Arquivo de tarefas de cada dupla (para enviar a cada um) | **Novo** |
| — | [jira/](jira/) | CSV para importar as tarefas no Jira | **Novo** |
| — | [decisoes/](decisoes/) | ADRs (decisões de arquitetura) | **Novo** |

## Como manter

- Mudou um requisito? Edite `02-requisitos.md` e registre na tabela de histórico de versões.
- Tomou uma decisão técnica relevante? Crie `decisoes/NNNN-titulo.md` (copie o formato do 0001).
- Resolveu uma dúvida de `08-questoes-em-aberto.md`? Mova para "Resolvidas" com a decisão e a data.
- **Não versione** os PDFs originais: o Pré-Projeto contém matrículas e e-mails da equipe e o repositório é público. Se quiser guardá-los localmente, use `docs/originais/` (já está no `.gitignore`).
