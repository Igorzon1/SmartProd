// Menu principal, igual em todas as páginas. Dona: dupla 1.
// Todas as páginas já estão listadas aqui; cada dupla só implementa a sua, sem editar este arquivo.
import { RAIZ } from './config.js';

export const ITENS_MENU = [
  { rotulo: 'Início', href: 'index.html', perfis: ['GERENTE', 'FUNCIONARIO'] },
  { rotulo: 'Kanban', href: 'pages/kanban/index.html', perfis: ['GERENTE', 'FUNCIONARIO'] },
  { rotulo: 'Execução', href: 'pages/execucao/index.html', perfis: ['GERENTE', 'FUNCIONARIO'] },
  { rotulo: 'Calculadora', href: 'pages/calculadora/index.html', perfis: ['GERENTE', 'FUNCIONARIO'] },
  { rotulo: 'Ordens de produção', href: 'pages/ordens-producao/index.html', perfis: ['GERENTE'] },
  { rotulo: 'Fichas técnicas', href: 'pages/fichas-tecnicas/index.html', perfis: ['GERENTE'] },
  { rotulo: 'Insumos', href: 'pages/insumos/index.html', perfis: ['GERENTE'] },
  { rotulo: 'Usuários', href: 'pages/usuarios/index.html', perfis: ['GERENTE'] },
  { rotulo: 'Dashboard', href: 'pages/dashboard/index.html', perfis: ['GERENTE'] }
];

/** Preenche o elemento <nav> com os links que o perfil pode ver. */
export function renderizarMenu(elemento, perfil) {
  const links = ITENS_MENU.filter((item) => item.perfis.includes(perfil)).map((item) => {
    const link = document.createElement('a');
    link.className = 'menu__link';
    link.href = new URL(item.href, RAIZ).href;
    link.textContent = item.rotulo;
    return link;
  });
  elemento.replaceChildren(...links);
}
