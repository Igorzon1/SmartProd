import { exigirLogin } from '../../js/core/auth.js';
import { renderizarMenu } from '../../js/core/menu.js';

const sessao = exigirLogin(['GERENTE']);
if (sessao) {
  renderizarMenu(document.getElementById('menu'), sessao.usuario.perfil);
  // Dupla 2: implemente a tela aqui (RF012–RF015). Chamadas à API: apiFetch() de js/core/api.js.
}
