# 01 — Visão e Escopo

> Fonte: *Documento de Visão e Escopo* (última atualização 16/10/2026).

## 1. Informações gerais

- **Projeto:** SmartProd
- **Equipe:** Ana, Breno, Giovanne, Igor, Ledson, Marcelo

## 2. Problema e nicho

**Problema:** despadronização e desperdício oculto na produção de padarias — perda de dinheiro por erros de dosagem, fornadas perdidas e variação de qualidade. O SmartProd transforma a cozinha artesanal numa esteira guiada e previsível: a receita sai sempre igual, independente de quem prepara, e o gerente enxerga custos, rendimento e desperdício em tempo real.

**Público-alvo:** proprietários, gerentes e gestores de pequenas e médias padarias, confeitarias e panificadoras (artesanais ou industriais).

## 3. A solução

Software de gestão de produção que padroniza o preparo passo a passo no chão de fábrica, controla a esteira em tempo real e calcula proporcionalmente os insumos.

**Benefícios:**

1. **Padronização e qualidade constante** — sabor, tamanho e qualidade idênticos em todas as fornadas.
2. **Redução direta de desperdícios** — menos erro de dosagem/pesagem, menos insumo e fornada perdidos.
3. **Previsibilidade e eficiência** — Kanban com alertas de tempo; evita falta de produto no horário de pico.
4. **Visibilidade financeira em tempo real** — rendimento das fornadas e custo real de cada produto para proteger a margem.

## 4. Escopo (o que SERÁ feito)

| # | Funcionalidade | Resumo | Requisitos |
|---|---|---|---|
| F1 | Gestão de acessos e perfis | Login por e-mail e senha; perfis Gerente e Funcionário | RF001, RF002, RF003 |
| F2 | Insumos e fichas técnicas | Cadastro de insumos e receitas; Porcentagem do Padeiro; modo de preparo | RF004, RF005, RF006 |
| F3 | Calculadora proporcional | Recalcula receita por unidades a produzir ou por estoque disponível | RF007 |
| F4 | Esteira Kanban e passo a passo | Painel de OPs por status; tela operacional com guia e temporizadores | RF008, RF009, RF010 |
| F5 | Registro de perdas e refugos | Funcionário registra desperdício informando etapa e motivo | RF011 |
| F6 | Dashboard operacional | Rendimento diário, perdas acumuladas, ranking de desperdício, status das OPs | RF012–RF015 |

## 5. Fora do escopo (NÃO fazer nesta versão)

- **Financeiro e notas fiscais:** sem fluxo de caixa, contas a pagar/receber, NFC-e/NF-e.
- **PDV:** sem integração com caixa, máquinas de cartão ou balanças de atendimento.
- **Compras com fornecedores:** sem pedidos automáticos nem cotação de insumos.
- **Análises preditivas avançadas:** sem sugestões por IA, gráficos de inflação de insumos ou margem de lucro por lote.
- **Ponto e RH:** sem horários, folha ou frequência.
- **App mobile nativo:** sem Play Store/App Store; acesso por navegador web responsivo.
