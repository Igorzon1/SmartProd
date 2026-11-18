# 08 — Questões em aberto

Inconsistências encontradas nos documentos e decisões ainda não tomadas. **IAs: não decidam sozinhas — pergunte ao usuário.** Quando resolver, mova o item para "Resolvidas" com data e decisão.

## Abertas

| # | Questão | Onde aparece | Sugestão |
|---|---|---|---|
| Q1 | Perfis: a Visão fala em 2 perfis (Gerente/Funcionário), os stakeholders têm 3 papéis (Gerente, Padeiro, Ajudante) e o mockup de login tem "Gerente/Padeiro" | Visão §4, Requisitos §2.2, Pré-Projeto | Manter 2 perfis de acesso; Padeiro e Ajudante são `FUNCIONARIO` |
| Q2 | RF007 recalcula "por estoque", mas não há requisito de cadastro/controle de estoque | RF007, Visão F3 | Gerente/padeiro **informa o estoque disponível na própria calculadora**; sem entidade de estoque nesta versão |
| Q4 | Disponibilidade 99,5% (RNF-C001) é incompatível com *free tier* que "dorme" | RNF-C001, restrição de orçamento | Tratar como meta de projeto; documentar a limitação na entrega |
| Q5 | Estados da OP (3: pendente, em andamento, concluída) × colunas do Kanban por etapa (pesagem, mistura, fermentação, forno…) × nome da coluna inicial ("Pendente" vs "A Fazer") | RF009, Canvas, Pré-Projeto | OP tem 3 estados; Kanban v1 usa as 3 colunas. Etapas viram o campo `etapaAtual` (evolução) |
| Q6 | Mockup de login mostra Google/Facebook, mas o escopo é só e-mail e senha | Pré-Projeto §7, Visão F1 | Fora do escopo; ajustar o mockup |
| Q7 | Conversão de unidades (g↔kg, ml↔L, un) no custo e no cálculo | RF004, RF006, RF007 | Normalizar para unidade base por tipo (massa, volume, contagem) |
| Q8 | Canvas lista análises sem RF: perdas por etapa e motivo, atrasos vs horário de entrega, custo real por produto, gargalo | Canvas §9 | Backlog pós-MVP, exceto o que os RF012–RF015 já cobrem |
| Q9 | Critérios completos e prazo final da A3 não estão neste repositório | [05-regras-a3.md](05-regras-a3.md) | Colar o documento de regras lá |
| Q10 | PDFs oficiais (Requisitos e Canvas) ainda dizem Node.js | RNF-Comp002, Restrições, Canvas §8 | Atualizar os PDFs/Canvas antes da entrega |
| Q11 | Como identificar a "farinha" (100%)? Pode haver mais de uma farinha | RF006 | Flag `ehFarinha` por ingrediente da ficha; soma das farinhas = 100% |

## Pequenas correções nos PDFs

- RF011 estava **sem status** no PDF (assumido "A implementar").
- RF007 tinha texto duplicado ("estoque. estoque").
- RNF-D002 cita "OIDs" (provável erro de digitação de "OPs").

## Resolvidas

- **2026-10-26** — Backend em Java/Spring Boot em vez de Node.js (ADR-0001).
- **2026-11-04** — Q3: autenticação por token JWT (HS256), stateless, com validade padrão de 8 h.
- **2026-11-16** — Q7 (só a Porcentagem do Padeiro, RF006): kg vira g e L vira ml antes da conta, e 1 ml conta como 1 g (vale para água e leite, é aproximado para óleo e mel). Ingrediente em `UN` e ficha sem farinha ficam com `percentualPadeiro` nulo. **Q7 continua aberta** para o custo (RF004) e para o recálculo da calculadora (RF007).
- **2026-11-17** — Q12: a perda é sempre do produto da OP, registrada em kg (`pesoKg`), com o número de unidades opcional. O valor em R$ não fica na perda: o dashboard calcula. Perda de ingrediente avulso (farinha derramada etc.) fica fora desta versão. Decisão do Igor na revisão do PR #17.
- **2026-11-17** — Q13: a quantidade produzida é obrigatória ao concluir a OP; a tela já vem com a quantidade prevista preenchida.
- **2026-11-18** — Q7 (custo das perdas no dashboard, RF013 e RF014): custo por kg da receita = custo da ficha ÷ peso da massa em kg. O custo soma quantidade × custo unitário (sem conversão, porque a quantidade já está na unidade do insumo); no peso, g e ml viram kg (1 ml = 1 g) e ingrediente em `UN` fica de fora. **Q7 continua aberta** para o recálculo da calculadora (RF007).
