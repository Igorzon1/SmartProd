# Frontend do SmartProd

HTML5 + CSS3 + JavaScript puro, sem build. Consome a API do backend (`../backend`).

## Rodar localmente

Com o backend no ar (`http://localhost:8080`), sirva esta pasta em qualquer servidor estático na porta **5500** (já liberada no CORS do backend):

```bash
npx serve -l 5500 .
```

ou, com Python:

```bash
python -m http.server 5500
```

ou use a extensão *Live Server* do VS Code (porta 5500 por padrão). Abra http://localhost:5500.

## Estrutura

```
index.html        página inicial (hoje só testa a conexão com a API)
css/style.css     estilos e variáveis de cor
js/config.js      URL da API (trocar em produção)
js/app.js         lógica da página
```

## Regras de interface (ver docs/02-requisitos.md)

- Interface do Funcionário: botões com área de toque ≥ 48 px, alto contraste, fontes grandes, no máximo 3 cliques por ação (RNF-U001, RNF-U002).
- Progresso da etapa atual da receita salvo em `localStorage` (RNF-C002).
- Nunca exibir custos ou valores em R$ para o perfil Funcionário (RNF-S002).
