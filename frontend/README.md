# Frontend do SmartProd

HTML5 + CSS3 + JavaScript puro (ES modules), sem build. Consome a API do backend (`../backend`).

## Rodar localmente

Com o backend no ar (`http://localhost:8080`), sirva esta pasta por HTTP na porta **5500** (já liberada no CORS do backend). Os scripts são módulos, então **não funciona abrindo o arquivo direto** (`file://`).

```bash
npx serve -l 5500 .
```

ou, com Python:

```bash
python -m http.server 5500
```

ou use a extensão *Live Server* do VS Code (porta 5500 por padrão). Abra http://localhost:5500.

## Estrutura e donos

```
index.html, js/home.js        página inicial (dupla 1)
css/base.css                  estilos e componentes compartilhados (dupla 1)
js/core/                      base comum: config, api, auth, menu (dupla 1)
pages/login, usuarios, insumos                    dupla 1
pages/fichas-tecnicas, calculadora, dashboard     dupla 2
pages/ordens-producao, kanban, execucao           dupla 3
```

Cada pasta em `pages/` tem `index.html` e `main.js`. CSS, mocks (`mock/*.json`) e outros arquivos da tela ficam **dentro da própria pasta** da tela. Só a dupla dona edita a sua pasta; `js/core/` e `css/base.css` só a dupla 1 (outras duplas pedem por PR separado). Veja [../docs/09-divisao-de-tarefas.md](../docs/09-divisao-de-tarefas.md).

## Como usar a base comum

```js
import { exigirLogin } from '../../js/core/auth.js';
import { renderizarMenu } from '../../js/core/menu.js';
import { apiFetch } from '../../js/core/api.js';

const sessao = exigirLogin(['GERENTE']);          // redireciona se não puder acessar
if (sessao) {
  renderizarMenu(document.getElementById('menu'), sessao.usuario.perfil);
  const insumos = await apiFetch('/api/insumos');  // já envia o token
}
```

Enquanto o login real não existe, `js/core/config.js` tem `usarSessaoFalsa: true` (e `perfilSessaoFalsa` para testar `GERENTE` ou `FUNCIONARIO`).

## Regras de interface (ver docs/02-requisitos.md)

- Interface do Funcionário: botões com área de toque ≥ 48 px, alto contraste, fontes grandes, no máximo 3 cliques por ação (RNF-U001, RNF-U002).
- Progresso da etapa atual da receita salvo em `localStorage` (RNF-C002).
- Nunca exibir custos ou valores em R$ para o perfil Funcionário (RNF-S002).
