# 04 — Canvas UC HUB (12 blocos)

> Fonte: *Canvas Ânima Hub — SmartProd*. Material acadêmico; serve de contexto para as IAs entenderem a motivação e o vocabulário do projeto. O Canvas entregue ainda cita Node.js como ferramenta (ver nota no bloco 8).

## 1. Objetivo geral da otimização do processo

Padronizar e controlar a produção de padarias e confeitarias, reduzindo o desperdício de insumos, as perdas de fornadas e a variação na qualidade dos produtos.

- **Processo:** produção de receitas no chão de fábrica, da Ordem de Produção (OP) até a fornada concluída.
- **Solução:** SmartProd, sistema web que transforma a cozinha artesanal numa esteira guiada: receita passo a passo, cálculo automático de ingredientes e Kanban de OPs.
- **Para o gerente:** controle em tempo real de custos, rendimento e desperdícios.
- **Público:** proprietários e gerentes de pequenas e médias padarias, confeitarias e panificadoras.

## 2. Pontos do processo que serão otimizados

- Dosagem e pesagem dos insumos (Porcentagem do Padeiro)
- Padronização do preparo: ficha técnica, passo a passo e temporizadores
- Sequência da produção: Kanban de Ordens de Produção
- Registro de perdas e refugos (etapa, quantidade, motivo)
- Controle de rendimento e do custo do desperdício

## 3. Pode ser modelado e simulado?

**Sim, um processo.** O fluxo de produção é modelado como Ordens de Produção (pendente, em andamento, concluída) e a receita como ficha técnica. Simula-se o recálculo dos insumos por demanda (unidades a produzir) ou por estoque disponível.

## 4. Pontos impactados pela mudança

- **Cozinha:** do papel e da memória para o preparo guiado em tablet ou computador.
- **Gerência:** cadastro e atualização dos custos dos insumos; acompanhamento pelo dashboard.
- **Equipe:** treinamento básico e registro de perdas na esteira.

## 5. Contribuições esperadas

- Menos desperdício de insumos e de fornadas perdidas
- Sabor, tamanho e qualidade iguais em toda fornada
- Menos falta de produtos nos horários de pico
- Custo real por produto e margem de lucro protegida

## 6. Desafios e riscos do modelo de simulação

- Abstração: clima, fermentação e forno variam e ficam fora do modelo.
- Escalar o lote nem sempre é linear.

## 7. Desafios e riscos da análise de dados

- Os dados exigem registro correto das perdas.
- Custos e perdas só para o Gerente (LGPD).
- Sem autorização especial; as regras da ANVISA continuam valendo.

## 8. Desafios e riscos das ferramentas

- Nuvem gratuita (hospedagem e banco): limites de uso.
- Depende de internet na cozinha.
- ~~Conhecimento inicial em JavaScript/Node.js: curva de aprendizado~~ → **ALTERADO:** o backend agora é **Java/Spring Boot** (ADR-0001); o risco passa a ser a curva de aprendizado do ecossistema Spring. *Atualizar o Canvas oficial para refletir isso.*

## 9. Análises estatísticas possíveis

- Rendimento diário: produzido × previsto
- Desperdício acumulado no período (kg e R$)
- Ranking dos 3 produtos mais desperdiçados
- Perdas por etapa e por motivo (onde e por que se perde)
- Status das OPs (pendentes, em andamento, concluídas) e atrasos frente ao horário de entrega
- Custo real de cada produto
- Maior gargalo da esteira: etapa com mais perdas ou atrasos

> Perdas por etapa e motivo, atrasos frente ao horário de entrega, custo real por produto e gargalo da esteira **não têm RF correspondente** em `02-requisitos.md` — candidatos a evolução (ver Q8).

## 10. Trabalhos relacionados

- CASER, Diego; PEREIRA, Raphael. Análise dos impactos da utilização de um software de gerenciamento de produção numa empresa de panificação e confeitaria. *Anais do II Seminário de Pesquisa e Produtividade da FESV e FESVV*, v. 2, n. 2, p. 1-12, out. 2020.
- VIANA, Iêda Cristina; CARREIRO, Lorena Falquêto; HERKENHOFF, Débora Athayde. Os benefícios da ordem de produção em uma panificadora do interior do estado do Espírito Santo: uma proposta a partir dos conceitos de PCP. *Revista Ambiente Acadêmico*, v. 1, n. 2, p. 59-80, 2015.
- MASANABO, F. I.; CHATUR, S. A hybrid push/pull system in an SME bakery in Johannesburg. *South African Journal of Industrial Engineering*, v. 34, n. 3, p. 258-269, nov. 2023. DOI: 10.7166/34-3-2962.

## 11. Referências científicas — problema

- BATISTA, Lorena Karla de Paula. *Gestão do desperdício como forma de redução dos custos de produção na Panificadora Art & Pães*. Formiga: IFMG, 2017.
- FERRAZ, Renato Ribeiro Nogueira et al. Redução de custos e melhoria dos padrões de gestão com a utilização de fichas técnicas de preparação para padronização da montagem de refeições em unidades hospitalares. *Revista dos Mestrados Profissionais*, v. 2, n. 2, 2013.
- SLACK, Nigel; CHAMBERS, Stuart; JOHNSTON, Robert. *Administração da produção*. 3. ed. São Paulo: Atlas, 2009.

## 12. Referências científicas — ferramentas

- HAMELMAN, Jeffrey. *Bread: a baker's book of techniques and recipes*. 3. ed. Hoboken: Wiley, 2021.
- OHNO, Taiichi. *Toyota production system: beyond large-scale production*. Portland: Productivity Press, 1988.
- TILKOV, Stefan; VINOSKI, Steve. Node.js: using JavaScript to build high-performance network programs. *IEEE Internet Computing*, v. 14, n. 6, p. 80-83, 2010. *(Referência de Node.js: com a mudança para Java/Spring Boot, considerar trocar por uma referência de Spring/Java.)*
