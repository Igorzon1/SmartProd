import { exigirLogin } from '../../js/core/auth.js';
import { renderizarMenu } from '../../js/core/menu.js';

const sessao = exigirLogin(['GERENTE']);
if (sessao) {
  renderizarMenu(document.getElementById('menu'), sessao.usuario.perfil);
  // Dupla 1: implemente a tela aqui (RF002). Chamadas à API: apiFetch() de js/core/api.js.
}
