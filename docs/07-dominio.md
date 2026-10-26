# 07 — Modelo de domínio e regras de negócio

> **Proposta** derivada de `02-requisitos.md` e do Pré-Projeto para guiar a implementação. Itens marcados *(?)* dependem de resposta em [08-questoes-em-aberto.md](08-questoes-em-aberto.md). Só `Usuario` já existe no código.

## 1. Entidades

```mermaid
erDiagram
  USUARIO ||--o{ ORDEM_PRODUCAO : "abre / é responsável"
  INSUMO ||--o{ FICHA_INGREDIENTE : "compõe"
  FICHA_TECNICA ||--|{ FICHA_INGREDIENTE : "tem"
  FICHA_TECNICA ||--|{ PASSO_PREPARO : "tem"
  FICHA_TECNICA ||--o{ ORDEM_PRODUCAO : "é produzida em"
  ORDEM_PRODUCAO ||--o{ REGISTRO_PERDA : "gera"
```

| Entidade | Campos principais | Requisitos |
|---|---|---|
| `Usuario` ✅ | nome, email (único, minúsculo), senhaHash, perfil (`GERENTE`/`FUNCIONARIO`), ativo, criadoEm | RF001, RF002 |
| `Insumo` | nome, unidadeMedida (`G`, `KG`, `ML`, `L`, `UN`), custoUnitario (R$ por unidade de medida), ativo | RF004 |
| `FichaTecnica` | nome, rendimentoPadrao (unidades por receita base), pesoUnidade *(?)*, ativa | RF005 |
| `FichaIngrediente` | fichaTecnica, insumo, quantidadeBase, ehFarinha | RF005, RF006 |
| `PassoPreparo` | fichaTecnica, ordem, etapa, descrição, duracaoSegundos (opcional → temporizador) | RF005, RF010 |
| `OrdemProducao` | fichaTecnica, quantidadeUnidades, horarioEntrega, responsável, status, criadoPor, iniciadaEm, concluidaEm | RF008, RF009, RF015 |
| `RegistroPerda` | ordemProducao, etapa, quantidade (+ unidade) *(?)*, motivo, registradoPor, registradoEm | RF011, RF013, RF014 |

## 2. Fórmulas

**Porcentagem do Padeiro (RF006).** A soma das quantidades dos ingredientes marcados como farinha vale 100%.

```
pct_i = quantidade_i / soma(quantidade das farinhas) × 100
```

Exemplo: farinha 1000 g, água 700 g, sal 20 g, fermento 10 g → 100%, 70%, 2%, 1%.

**Recálculo por demanda (RF007).**

```
fator          = unidades_desejadas / rendimentoPadrao
quantidade_i'  = quantidade_i × fator
```

**Recálculo por estoque disponível (RF007).**

```
fator_i        = estoque_i / quantidade_i            (para cada ingrediente)
fator          = menor fator_i                        (o ingrediente limitante)
unidades       = floor(fator × rendimentoPadrao)
```

O resultado deve informar também **qual ingrediente limita** a produção. *(? Q2: de onde vem o estoque.)*

**Custo.**

```
custo_receita  = soma(quantidade_i × insumo_i.custoUnitario)   (com conversão de unidades — Q7)
custo_unidade  = custo_receita / rendimentoPadrao
custo_perda    = valor em R$ da quantidade perdida (Q12)
```

**Dashboard.**

```
rendimento_diario_% = produzido_no_dia / previsto_no_dia × 100     (previsto = soma das OPs do dia)
desperdicio_periodo = soma(RegistroPerda) em kg e em R$
ranking             = 3 produtos com maior desperdício no período
```

## 3. Ciclo de vida da OP

```mermaid
stateDiagram-v2
  [*] --> PENDENTE: gerente abre a OP (RF008)
  PENDENTE --> EM_ANDAMENTO: funcionário clica Iniciar
  EM_ANDAMENTO --> CONCLUIDA: fornada concluída
```

Regras: só o Gerente abre OP; Funcionário inicia e conclui; perdas só podem ser registradas em OP `EM_ANDAMENTO`; OP concluída fica imutável. O Kanban agrupa por status (RF009) — ver Q5 sobre colunas por etapa.

## 4. Regras de acesso

| Recurso | GERENTE | FUNCIONARIO |
|---|---|---|
| Usuários | CRUD/inativar | — |
| Insumos (com custo) | CRUD | só leitura **sem custo** |
| Fichas técnicas | CRUD | leitura (ingredientes e passos, sem custo) |
| Abrir OP | sim | não |
| Kanban / executar OP | ver tudo | ver/iniciar/concluir |
| Registrar perda | sim | sim |
| Dashboard e relatórios (R$) | sim | **não** (RNF-S002) |
